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
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    private static final String MODELS_PATH = "/models";

    private final ProviderCredentialMapper credentialMapper;
    private final ModelProfileMapper profileMapper;
    private final ProviderSecretCipher secretCipher;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;
    private final CustomModelCapabilityDiscovery capabilityDiscovery;
    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;
    private final ObjectMapper objectMapper;

    public ModelProfile requireProfile(Long accountId) {
        ModelProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        if (profile == null) {
            throw BusinessException.badRequest("请先配置模型服务");
        }
        return profile;
    }

    public String resolveApiKey(Long accountId, ModelProfile profile) {
        if (profile.getCredentialId() == null) {
            return null;
        }
        ProviderCredential credential = credentialMapper.selectById(profile.getCredentialId());
        if (credential == null || !accountId.equals(credential.getAccountId())) {
            throw BusinessException.badRequest("模型凭据不可用，请重新配置");
        }
        return secretCipher.decrypt(credential.getApiKeyEncrypted());
    }

    public String resolveApiKey(Long accountId, Long credentialId) {
        if (credentialId == null) {
            return null;
        }
        ProviderCredential credential = credentialMapper.selectById(credentialId);
        if (credential == null || !accountId.equals(credential.getAccountId())) {
            throw BusinessException.badRequest("模型凭据不可用，请重新配置");
        }
        return secretCipher.decrypt(credential.getApiKeyEncrypted());
    }

    @Transactional(rollbackFor = Exception.class)
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
            customEndpointUrl = normalizeRoot(command.customEndpointUrl(), provider);
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
            ? capabilityDiscovery.discover(provider, customEndpointUrl, effectiveApiKey, model)
            : capabilityCatalog.capability(provider, model);
        ModelCapabilityResponse.ReasoningLevel level = reasoningLevels.parse(command.reasoningLevel());
        if (!capability.supportedReasoningLevels().contains(level)) {
            throw BusinessException.badRequest("所选模型不支持该思考深度");
        }

        List<String> fallbackModels = validateFallbackModels(
            provider, model, level, command.fallbackModels());

        Long credentialId;
        if (newKey) {
            credentialId = createCredential(accountId, provider, credentialScope,
                secretCipher.encrypt(command.apiKey()));
        } else if (clearKey) {
            credentialId = null;
        } else {
            credentialId = reusableCredentialId;
        }

        ModelProfile profile = existing == null ? new ModelProfile() : existing;
        profile.setAccountId(accountId);
        profile.setProvider(provider);
        profile.setModel(model);
        profile.setCredentialId(credentialId);
        profile.setCustomEndpointUrl(customEndpointUrl);
        profile.setReasoningLevel(level.name());
        profile.setModelCapabilityJson(CustomLlmProtocol.isCustom(provider) ? toCapabilityJson(capability) : null);
        profile.setFallbackModelsJson(toJson(fallbackModels));
        if (existing == null) {
            try {
                profileMapper.insert(profile);
            } catch (org.springframework.dao.DuplicateKeyException race) {
                throw BusinessException.revisionConflict("模型配置已被其他请求更新，请重试");
            }
        } else {
            profileMapper.updateById(profile);
        }
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
        return new ModelConfigurationView(
            profile.getProvider(),
            profile.getModel(),
            profile.getCustomEndpointUrl(),
            hasApiKey,
            masked,
            profile.getReasoningLevel(),
            fromJson(profile.getFallbackModelsJson()),
            capability
        );
    }

    public List<ProviderDescriptorView> listModels(Long accountId) {
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
        String baseUrl = normalizeRoot(command.baseUrl(), command.provider());
        String apiKey = command.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = activeKeyForScope(accountId, command.provider(), baseUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }
        String modelsUrl = baseUrl + MODELS_PATH;
        egressPolicy.validateConfiguredEndpoint(modelsUrl);
        Request.Builder requestBuilder = new Request.Builder().url(modelsUrl).get();
        if (protocol == CustomLlmProtocol.ANTHROPIC_MESSAGES) {
            requestBuilder
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
        Request request = requestBuilder.build();
        try (Response response = egressHttpClientFactory.discoveryClient()
            .newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw BusinessException.badRequest("鉴权失败，请检查 API Key");
            }
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("Base URL 不可达或模型列表接口返回异常：" + response.code());
            }
            String body = response.body() == null ? "" : response.body().string();
            List<ModelCapabilityResponse> models = parseDiscoveredModels(command.provider(), body);
            return new DiscoveredModelsView(baseUrl, models);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.badRequest("Base URL 不可达，请检查 Base URL");
        }
    }

    public ModelCapabilityResponse discoverCustomModelCapability(
        Long accountId,
        com.prelude.llm.api.LlmPort.DiscoverModelCapabilityCommand command
    ) {
        CustomLlmProtocol.require(command.provider());
        String baseUrl = normalizeRoot(command.baseUrl(), command.provider());
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
        return capabilityDiscovery.discover(command.provider(), baseUrl, apiKey, model);
    }

    ModelCapabilityResponse capabilityForProfile(ModelProfile profile, String model) {
        if (!CustomLlmProtocol.isCustom(profile.getProvider()) || !profile.getModel().equals(model)) {
            return capabilityCatalog.capability(profile.getProvider(), model);
        }
        ModelCapabilityResponse stored = fromCapabilityJson(profile.getModelCapabilityJson());
        if (stored == null
            || !profile.getProvider().equals(stored.provider())
            || !model.equals(stored.model())) {
            return capabilityCatalog.capability(profile.getProvider(), model);
        }
        return stored;
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
            provider, model, null, false, null, "AUTO", List.of(),
            capability);
    }

    private List<String> validateFallbackModels(
        String provider,
        String primaryModel,
        ModelCapabilityResponse.ReasoningLevel reasoningLevel,
        List<String> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<String> validated = new ArrayList<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                throw BusinessException.badRequest("回退模型不能为空");
            }
            String model = raw.trim();
            if (model.equals(primaryModel)) {
                throw BusinessException.badRequest("主模型不能同时出现在回退模型中");
            }
            if (validated.contains(model)) {
                throw BusinessException.badRequest("回退模型不能重复");
            }
            capabilityCatalog.requireSupportedModel(provider, model);
            if (!capabilityCatalog.capability(provider, model)
                .supportedReasoningLevels().contains(reasoningLevel)) {
                throw BusinessException.badRequest("回退模型不支持所选思考深度");
            }
            validated.add(model);
        }
        return List.copyOf(validated);
    }

    private String normalizeRoot(String input, String provider) {
        try {
            URI uri = URI.create(input.trim());
            String path = uri.getPath() == null ? "" : pathTrim(uri.getPath());
            if (CustomLlmProtocol.isCustom(provider)) {
                path = stripEndpointSuffix(path, CustomLlmProtocol.require(provider).endpointSuffix());
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                path.isBlank() ? null : path, null, null).toString();
        } catch (Exception exception) {
            throw BusinessException.badRequest("Base URL 格式不正确");
        }
    }

    private String pathTrim(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String stripEndpointSuffix(String path, String suffix) {
        return path.endsWith(suffix) ? pathTrim(path.substring(0, path.length() - suffix.length())) : path;
    }

    private List<ModelCapabilityResponse> parseDiscoveredModels(String provider, String body) {
        try {
            List<ModelCapabilityResponse> models = new ArrayList<>();
            tools.jackson.databind.JsonNode root = objectMapper.readTree(body);
            for (tools.jackson.databind.JsonNode node : root.path("data")) {
                String id = node.path("id").asString(null);
                if (id != null && !id.isBlank()) {
                    models.add(capabilityCatalog.capability(provider, id));
                }
            }
            return List.copyOf(models);
        } catch (Exception exception) {
            throw BusinessException.badRequest("模型列表响应格式不正确");
        }
    }

    private String toJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("配置序列化失败");
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String toCapabilityJson(ModelCapabilityResponse capability) {
        try {
            return objectMapper.writeValueAsString(capability);
        } catch (Exception exception) {
            throw BusinessException.badRequest("模型能力序列化失败");
        }
    }

    private ModelCapabilityResponse fromCapabilityJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ModelCapabilityResponse.class);
        } catch (Exception exception) {
            return null;
        }
    }

}
