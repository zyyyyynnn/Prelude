package com.prelude.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.DiscoverModelsCommand;
import com.prelude.llm.api.LlmPort.DiscoveredModelsView;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ProviderDescriptorView;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;
import com.prelude.llm.persistence.ProviderCredential;
import com.prelude.llm.persistence.ProviderCredentialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Account-scoped model configuration: ProviderCredential (BYOK, AES-GCM at
 * rest) + ModelProfile. Built-in providers may use the deployment system
 * credential; custom endpoints always require an account credential in the
 * same scope. A scope change clears an incompatible saved key instead of
 * silently reusing it across boundaries.
 */
@Service
@RequiredArgsConstructor
public class ModelProfileService {

    private final ProviderCredentialMapper credentialMapper;
    private final ModelProfileMapper profileMapper;
    private final ProviderSecretCipher secretCipher;
    private final ProviderCredentialResolver credentialResolver;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;
    private final CustomModelCapabilityDiscovery capabilityDiscovery;
    private final CustomModelCatalogClient catalogClient;
    private final CustomLlmEgressPolicy egressPolicy;
    private final ModelCapabilityJson capabilityJson;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public String resolveApiKey(Long accountId, Long credentialId) {
        return credentialResolver.resolve(accountId, credentialId);
    }

    public ModelConfigurationView saveConfiguration(Long accountId, SaveConfigurationCommand command) {
        String provider = command.provider();
        if (!capabilityCatalog.knownProviders().contains(provider)) {
            throw BusinessException.badRequest("不支持的模型接入方式");
        }
        String customEndpointUrl = null;
        if (CustomLlmProtocol.isCustom(provider)) {
            if (command.customEndpointUrl() == null || command.customEndpointUrl().isBlank()) {
                throw BusinessException.badRequest("自定义端点必须填写 Base URL");
            }
            customEndpointUrl = EndpointRoots.normalize(command.customEndpointUrl(), provider);
            egressPolicy.validateConfiguredEndpoint(customEndpointUrl);
        }
        String model = command.model() == null ? "" : command.model().trim();
        capabilityCatalog.requireSupportedModel(provider, model);

        ModelProfile existing = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        String credentialScope = customEndpointUrl == null ? ProviderCredential.SYSTEM_SCOPE : customEndpointUrl;
        boolean clearKey = SaveConfigurationCommand.CLEAR_API_KEY.equals(command.apiKey());
        boolean newKey = command.apiKey() != null && !command.apiKey().isBlank() && !clearKey;
        Long reusableCredentialId = newKey || clearKey
            ? null
            : reusableActiveCredentialId(existing, accountId, provider, credentialScope);
        String effectiveApiKey;
        if (newKey) {
            effectiveApiKey = command.apiKey();
        } else if (reusableCredentialId != null) {
            effectiveApiKey = secretCipher.decrypt(
                credentialMapper.selectById(reusableCredentialId).getApiKeyEncrypted());
        } else {
            effectiveApiKey = null;
        }
        if (CustomLlmProtocol.isCustom(provider) && (effectiveApiKey == null || effectiveApiKey.isBlank())) {
            throw BusinessException.badRequest("自定义端点必须配置 API Key");
        }

        ModelCapabilityResponse capability = CustomLlmProtocol.isCustom(provider)
            ? capabilityDiscovery.discover(accountId, provider, customEndpointUrl, effectiveApiKey, model)
            : capabilityCatalog.capability(provider, model);
        ModelCapabilityResponse.ReasoningLevel level = reasoningLevels.parse(command.reasoningLevel());
        if (!capability.supportedReasoningLevels().contains(level)) {
            throw BusinessException.badRequest("所选模型不支持该思考深度");
        }
        ModelExecutionParameters executionParameters = ModelExecutionParameters.resolve(command.maxOutputTokens());

        List<ModelCapabilityResponse> fallbackCapabilities = validateFallbackCapabilities(
            accountId, provider, customEndpointUrl, effectiveApiKey, model, level, command.fallbackModels());

        PreparedConfiguration prepared = new PreparedConfiguration(
            provider, customEndpointUrl, model, credentialScope, level, executionParameters,
            capability, fallbackCapabilities, newKey, clearKey, reusableCredentialId,
            existing == null ? null : existing.getId());
        transactionTemplate.executeWithoutResult(status -> persistConfiguration(accountId, command, prepared));
        return currentConfiguration(accountId);
    }

    public ModelConfigurationView currentConfiguration(Long accountId) {
        ModelProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        if (profile == null) {
            return defaultConfiguration();
        }
        boolean hasApiKey = profile.getCredentialId() != null;
        String masked = null;
        if (hasApiKey) {
            ProviderCredential credential = credentialMapper.selectById(profile.getCredentialId());
            masked = credential == null ? null : secretCipher.mask(credential.getApiKeyEncrypted());
        }
        ModelCapabilityResponse capability = capabilityForProfile(profile, profile.getModel());
        ModelExecutionParameters executionParameters = ModelExecutionParameters.fromProfileJson(
            profile.getEffectiveParametersJson(), objectMapper);
        return new ModelConfigurationView(
            profile.getProvider(),
            profile.getModel(),
            profile.getCustomEndpointUrl(),
            hasApiKey,
            masked,
            profile.getReasoningLevel(),
            executionParameters.maxOutputTokens(),
            capabilityJson.readList(profile.getFallbackCapabilitiesJson()).stream()
                .map(ModelCapabilityResponse::model)
                .toList(),
            capability
        );
    }

    public List<ProviderDescriptorView> listModels() {
        List<ProviderDescriptorView> descriptors = new ArrayList<>();
        for (String provider : capabilityCatalog.knownProviders()) {
            boolean customEndpoint = CustomLlmProtocol.isCustom(provider);
            descriptors.add(new ProviderDescriptorView(
                provider,
                capabilityCatalog.displayName(provider),
                customEndpoint,
                capabilityCatalog.models(provider)
            ));
        }
        return descriptors;
    }

    /**
     * Protocol-specific /models discovery against a custom endpoint using the
     * draft key from the form, or the saved key of the same scope.
     */
    public DiscoveredModelsView discoverCustomModels(Long accountId, DiscoverModelsCommand command) {
        CustomLlmProtocol protocol = CustomLlmProtocol.require(command.provider());
        String baseUrl = EndpointRoots.normalize(command.baseUrl(), command.provider());
        String apiKey = command.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = activeKeyForScope(accountId, command.provider(), baseUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }
        return new DiscoveredModelsView(baseUrl, catalogClient.listModels(protocol, baseUrl, apiKey));
    }

    public ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        com.prelude.llm.api.LlmPort.DiscoverModelCapabilityCommand command
    ) {
        CustomLlmProtocol.require(command.provider());
        String baseUrl = EndpointRoots.normalize(command.baseUrl(), command.provider());
        String model = command.model() == null ? "" : command.model().trim();
        if (model.isBlank()) {
            throw BusinessException.badRequest("模型不能为空");
        }
        String apiKey = command.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = activeKeyForScope(accountId, command.provider(), baseUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }
        return capabilityDiscovery.discover(accountId, command.provider(), baseUrl, apiKey, model);
    }

    ModelCapabilityResponse capabilityForProfile(ModelProfile profile, String model) {
        return ProfileCapabilities.capabilityForProfile(profile, model, capabilityCatalog, capabilityJson);
    }

    private String activeKeyForScope(Long accountId, String provider, String baseUrl) {
        ModelProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        Long credentialId = reusableActiveCredentialId(
            profile, accountId, provider, baseUrl);
        return credentialId == null
            ? null
            : secretCipher.decrypt(credentialMapper.selectById(credentialId).getApiKeyEncrypted());
    }

    private Long reusableActiveCredentialId(ModelProfile profile, Long accountId, String provider, String scope) {
        if (profile == null || profile.getCredentialId() == null
            || !provider.equals(profile.getProvider())) {
            return null;
        }
        ProviderCredential credential = credentialMapper.selectById(profile.getCredentialId());
        if (credential == null
            || !accountId.equals(credential.getAccountId())
            || !provider.equals(credential.getProvider())
            || !scope.equals(credential.getScopeKey())) {
            return null;
        }
        return credential.getId();
    }

    private Long createCredential(Long accountId, String provider, String scope, String encryptedKey) {
        ProviderCredential credential = new ProviderCredential();
        credential.setAccountId(accountId);
        credential.setProvider(provider);
        credential.setScopeKey(scope);
        credential.setApiKeyEncrypted(encryptedKey);
        credentialMapper.insert(credential);
        return credential.getId();
    }

    private ModelConfigurationView defaultConfiguration() {
        String provider = ModelCapabilityCatalog.PROVIDER_DEEPSEEK;
        String model = "deepseek-v4-pro";
        ModelCapabilityResponse capability = capabilityCatalog.capability(provider, model);
        return new ModelConfigurationView(
            provider, model, null, false, null, "AUTO",
            ModelExecutionParameters.DEFAULT_MAX_OUTPUT_TOKENS, List.of(),
            capability);
    }

    private List<ModelCapabilityResponse> validateFallbackCapabilities(
        Long accountId,
        String provider,
        String customEndpointUrl,
        String apiKey,
        String primaryModel,
        ModelCapabilityResponse.ReasoningLevel reasoningLevel,
        List<String> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<ModelCapabilityResponse> validated = new ArrayList<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                throw BusinessException.badRequest("回退模型不能为空");
            }
            String model = raw.trim();
            if (model.equals(primaryModel)) {
                throw BusinessException.badRequest("主模型不能同时出现在回退模型中");
            }
            if (validated.stream().anyMatch(capability -> capability.model().equals(model))) {
                throw BusinessException.badRequest("回退模型不能重复");
            }
            capabilityCatalog.requireSupportedModel(provider, model);
            ModelCapabilityResponse capability = CustomLlmProtocol.isCustom(provider)
                ? capabilityDiscovery.discover(accountId, provider, customEndpointUrl, apiKey, model)
                : capabilityCatalog.capability(provider, model);
            if (!capability.supportedReasoningLevels().contains(reasoningLevel)) {
                throw BusinessException.badRequest("回退模型不支持所选思考深度");
            }
            validated.add(capability);
        }
        return List.copyOf(validated);
    }

    private void persistConfiguration(
        Long accountId,
        SaveConfigurationCommand command,
        PreparedConfiguration prepared
    ) {
        ModelProfile current = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1 FOR UPDATE"));
        if ((prepared.expectedProfileId() == null && current != null)
            || (prepared.expectedProfileId() != null
            && (current == null || !prepared.expectedProfileId().equals(current.getId())))) {
            throw BusinessException.revisionConflict("模型配置已被其他请求更新，请重试");
        }

        Long credentialId;
        if (prepared.newKey()) {
            credentialId = createCredential(accountId, prepared.provider(), prepared.credentialScope(),
                secretCipher.encrypt(command.apiKey()));
        } else if (prepared.clearKey()) {
            credentialId = null;
        } else {
            Long currentCredentialId = reusableActiveCredentialId(
                current, accountId, prepared.provider(), prepared.credentialScope());
            if (!Objects.equals(currentCredentialId, prepared.reusableCredentialId())) {
                throw BusinessException.revisionConflict("模型凭据已被更新，请重试");
            }
            credentialId = currentCredentialId;
        }

        ModelProfile profile = current == null ? new ModelProfile() : current;
        profile.setAccountId(accountId);
        profile.setProvider(prepared.provider());
        profile.setModel(prepared.model());
        profile.setCredentialId(credentialId);
        profile.setCustomEndpointUrl(prepared.customEndpointUrl());
        profile.setReasoningLevel(prepared.reasoningLevel().name());
        profile.setEffectiveParametersJson(prepared.executionParameters().toJson(objectMapper));
        profile.setModelCapabilityJson(CustomLlmProtocol.isCustom(prepared.provider())
            ? capabilityJson.write(prepared.capability())
            : null);
        profile.setFallbackCapabilitiesJson(capabilityJson.writeList(prepared.fallbackCapabilities()));
        if (current == null) {
            try {
                profileMapper.insert(profile);
            } catch (org.springframework.dao.DuplicateKeyException race) {
                throw BusinessException.revisionConflict("模型配置已被其他请求更新，请重试");
            }
        } else {
            profileMapper.updateById(profile);
        }
    }

    private record PreparedConfiguration(
        String provider,
        String customEndpointUrl,
        String model,
        String credentialScope,
        ModelCapabilityResponse.ReasoningLevel reasoningLevel,
        ModelExecutionParameters executionParameters,
        ModelCapabilityResponse capability,
        List<ModelCapabilityResponse> fallbackCapabilities,
        boolean newKey,
        boolean clearKey,
        Long reusableCredentialId,
        Long expectedProfileId
    ) {
    }

}
