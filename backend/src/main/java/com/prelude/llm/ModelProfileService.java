package com.prelude.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort.DiscoverModelsCommand;
import com.prelude.llm.api.LlmPort.DiscoveredModelsView;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ModelDescriptorView;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
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
    private final ModelExecutionSnapshotMapper snapshotMapper;
    private final ProviderSecretCipher secretCipher;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final ReasoningLevels reasoningLevels;
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
        if (ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            if (command.customEndpointUrl() == null || command.customEndpointUrl().isBlank()) {
                throw BusinessException.badRequest("自定义端点必须填写 Base URL");
            }
            customEndpointUrl = normalizeRoot(command.customEndpointUrl());
            egressPolicy.validateConfiguredEndpoint(customEndpointUrl);
        }

        ModelCapabilityResponse.ReasoningLevel level = reasoningLevels.parse(command.reasoningLevel());
        if (!capabilityCatalog.capability(provider, command.model()).supportedReasoningLevels().contains(level)) {
            throw BusinessException.badRequest("所选模型不支持该思考深度");
        }

        ModelProfile existing = profileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getAccountId, accountId)
            .last("LIMIT 1"));
        Long credentialId = existing == null ? null : existing.getCredentialId();
        boolean scopeChanged = existing == null
            || !provider.equals(existing.getProvider())
            || !equalsNullable(customEndpointUrl, existing.getCustomEndpointUrl());

        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            if (SaveConfigurationCommand.CLEAR_API_KEY.equals(command.apiKey())) {
                credentialId = null;
            } else {
                credentialId = upsertCredential(accountId, provider, customEndpointUrl,
                    secretCipher.encrypt(command.apiKey()));
            }
        } else if (scopeChanged && credentialId != null) {
            // Provider/base URL changed: an old-scope key must not leak into the new scope.
            credentialId = null;
        }
        if (ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE.equals(provider) && credentialId == null) {
            throw BusinessException.badRequest("自定义端点必须配置 API Key");
        }

        List<String> fallbackModels = command.fallbackModels() == null
            ? List.of()
            : List.copyOf(command.fallbackModels());
        if (fallbackModels.contains(command.model())) {
            fallbackModels = new ArrayList<>(fallbackModels);
            fallbackModels.remove(command.model());
            fallbackModels = List.copyOf(fallbackModels);
        }

        ModelProfile profile = existing == null ? new ModelProfile() : existing;
        profile.setAccountId(accountId);
        profile.setProvider(provider);
        profile.setModel(command.model());
        profile.setCredentialId(credentialId);
        profile.setCustomEndpointUrl(customEndpointUrl);
        profile.setReasoningLevel(level.name());
        profile.setFallbackModelsJson(toJson(fallbackModels));
        if (existing == null) {
            profileMapper.insert(profile);
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
        ModelCapabilityResponse capability = capabilityCatalog.capability(
            profile.getProvider(), profile.getModel());
        return new ModelConfigurationView(
            profile.getProvider(),
            profile.getModel(),
            profile.getCustomEndpointUrl(),
            hasApiKey,
            masked,
            profile.getReasoningLevel(),
            fromJson(profile.getFallbackModelsJson()),
            capability.reasoning(),
            capability.supportedReasoningLevels(),
            List.of()
        );
    }

    public List<ModelDescriptorView> listModels(Long accountId) {
        List<ModelDescriptorView> descriptors = new ArrayList<>();
        for (String provider : capabilityCatalog.knownProviders()) {
            if (ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
                descriptors.add(new ModelDescriptorView(provider,
                    capabilityCatalog.displayName(provider), "", true, List.of()));
            } else {
                descriptors.add(new ModelDescriptorView(provider,
                    capabilityCatalog.displayName(provider), "", false, List.of()));
            }
        }
        return descriptors;
    }

    /**
     * OpenAI-compatible /models discovery against a custom endpoint using the
     * draft key from the form, or the saved key of the same scope.
     */
    public DiscoveredModelsView discoverCustomModels(Long accountId, DiscoverModelsCommand command) {
        String baseUrl = normalizeRoot(command.baseUrl());
        String apiKey = command.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = savedKeyForScope(accountId, baseUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.badRequest("API Key 不能为空");
        }
        String modelsUrl = baseUrl + MODELS_PATH;
        egressPolicy.validateConfiguredEndpoint(modelsUrl);
        Request request = new Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer " + apiKey)
            .get()
            .build();
        try (Response response = egressHttpClientFactory.discoveryClient()
            .newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw BusinessException.badRequest("鉴权失败，请检查 API Key");
            }
            if (!response.isSuccessful()) {
                throw BusinessException.badRequest("Base URL 不可达或模型列表接口返回异常：" + response.code());
            }
            String body = response.body() == null ? "" : response.body().string();
            List<String> models = parseModelIds(body);
            return new DiscoveredModelsView(baseUrl, models);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.badRequest("Base URL 不可达，请检查 Base URL");
        }
    }

    private String savedKeyForScope(Long accountId, String baseUrl) {
        ProviderCredential credential = credentialMapper.selectOne(
            new LambdaQueryWrapper<ProviderCredential>()
                .eq(ProviderCredential::getAccountId, accountId)
                .eq(ProviderCredential::getProvider, ModelCapabilityCatalog.PROVIDER_OPENAI_COMPATIBLE)
                .eq(ProviderCredential::getScopeKey, baseUrl)
                .last("LIMIT 1"));
        return credential == null ? null : secretCipher.decrypt(credential.getApiKeyEncrypted());
    }

    private Long upsertCredential(Long accountId, String provider, String scopeKey, String encryptedKey) {
        String scope = scopeKey == null ? ProviderCredential.SYSTEM_SCOPE : scopeKey;
        ProviderCredential existing = credentialMapper.selectOne(
            new LambdaQueryWrapper<ProviderCredential>()
                .eq(ProviderCredential::getAccountId, accountId)
                .eq(ProviderCredential::getProvider, provider)
                .eq(ProviderCredential::getScopeKey, scope)
                .last("LIMIT 1"));
        if (existing == null) {
            ProviderCredential credential = new ProviderCredential();
            credential.setAccountId(accountId);
            credential.setProvider(provider);
            credential.setScopeKey(scope);
            credential.setApiKeyEncrypted(encryptedKey);
            credentialMapper.insert(credential);
            return credential.getId();
        }
        existing.setApiKeyEncrypted(encryptedKey);
        credentialMapper.updateById(existing);
        return existing.getId();
    }

    private ModelConfigurationView defaultConfiguration() {
        String provider = ModelCapabilityCatalog.PROVIDER_DEEPSEEK;
        ModelCapabilityResponse capability = capabilityCatalog.capability(provider, "");
        return new ModelConfigurationView(
            provider, "", null, false, null, "AUTO", List.of(),
            capability.reasoning(), capability.supportedReasoningLevels(), List.of());
    }

    private String normalizeRoot(String input) {
        try {
            URI uri = URI.create(input.trim());
            String path = uri.getPath() == null ? "" : pathTrim(uri.getPath());
            if (path.endsWith("/chat/completions") || path.endsWith("/responses")
                || path.endsWith("/messages")) {
                path = pathTrim(path.substring(0, path.lastIndexOf('/')));
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

    private List<String> parseModelIds(String body) {
        try {
            List<String> ids = new ArrayList<>();
            tools.jackson.databind.JsonNode root = objectMapper.readTree(body);
            for (tools.jackson.databind.JsonNode node : root.path("data")) {
                String id = node.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
            return ids;
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

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
