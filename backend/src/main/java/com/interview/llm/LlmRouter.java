package com.interview.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.LlmProviderResponse;
import com.interview.entity.LlmProviderConfig;
import com.interview.entity.User;
import com.interview.mapper.LlmProviderConfigMapper;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRouter {

    private final UserMapper userMapper;
    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final ObjectMapper objectMapper;
    private final List<LlmProvider> providers;

    public List<LlmProviderResponse> listEnabledProviders() {
        return llmProviderConfigMapper.selectList(new LambdaQueryWrapper<LlmProviderConfig>()
                .eq(LlmProviderConfig::getEnabled, 1)
                .orderByAsc(LlmProviderConfig::getId))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public LlmSelection resolveCurrentUserSelection() {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(user.getLlmProvider());
        String model = normalizeModel(user.getLlmModel(), provider.defaultModel());
        validateProviderSelection(provider.providerKey(), model);
        return new LlmSelection(provider.providerKey(), model);
    }

    public String chatCurrentUser(List<Map<String, String>> messages) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(user.getLlmProvider());
        String model = normalizeModel(user.getLlmModel(), provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), model);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        return provider.chat(new LlmProvider.LlmInvocation(providerConfig.getBaseUrl(), model, apiKey, messages));
    }

    public void streamCurrentUser(List<Map<String, String>> messages, Consumer<String> onDelta) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(user.getLlmProvider());
        String model = normalizeModel(user.getLlmModel(), provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), model);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        provider.streamChat(new LlmProvider.LlmInvocation(providerConfig.getBaseUrl(), model, apiKey, messages), onDelta);
    }

    public String chatWithSnapshot(String providerKey, String model, List<Map<String, String>> messages) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(providerKey);
        String normalizedModel = normalizeModel(model, provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), normalizedModel);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        return provider.chat(new LlmProvider.LlmInvocation(providerConfig.getBaseUrl(), normalizedModel, apiKey, messages));
    }

    public void streamWithSnapshot(String providerKey, String model, List<Map<String, String>> messages, Consumer<String> onDelta) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(providerKey);
        String normalizedModel = normalizeModel(model, provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), normalizedModel);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        provider.streamChat(new LlmProvider.LlmInvocation(providerConfig.getBaseUrl(), normalizedModel, apiKey, messages), onDelta);
    }

    public LlmProviderConfig validateProviderSelection(String providerKey, String model) {
        LlmProviderConfig providerConfig = requireEnabledProviderConfig(providerKey);
        LlmProvider provider = requireProvider(providerKey);
        String normalizedModel = normalizeModel(model, provider.defaultModel());
        String availableModels = providerConfig.getAvailableModels();
        if (availableModels != null && !availableModels.isBlank()) {
            List<String> models = parseModels(availableModels);
            if (!models.isEmpty() && !models.contains(normalizedModel)) {
                throw BusinessException.badRequest("所选模型不在可用列表中");
            }
        }
        return providerConfig;
    }

    private User requireCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user;
    }

    private LlmProvider requireProvider(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            throw BusinessException.badRequest("Provider 不能为空");
        }
        return providers.stream()
            .filter(provider -> provider.providerKey().equalsIgnoreCase(providerKey))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换 Provider"));
    }

    private LlmProviderConfig requireEnabledProviderConfig(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            throw BusinessException.badRequest("Provider 不能为空");
        }
        LlmProviderConfig providerConfig = llmProviderConfigMapper.selectOne(new LambdaQueryWrapper<LlmProviderConfig>()
            .eq(LlmProviderConfig::getProviderKey, providerKey)
            .eq(LlmProviderConfig::getEnabled, 1)
            .last("LIMIT 1"));
        if (providerConfig == null) {
            throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换 Provider");
        }
        return providerConfig;
    }

    private String normalizeModel(String model, String defaultModel) {
        return (model == null || model.isBlank()) ? defaultModel : model;
    }

    private String resolveApiKey(String encryptedUserKey, LlmProvider provider) {
        String userKey = decryptUserApiKey(encryptedUserKey);
        if (userKey != null && !userKey.isBlank()) {
            return userKey;
        }
        String systemApiKey = provider.systemApiKey();
        if (systemApiKey != null && !systemApiKey.isBlank()) {
            return systemApiKey;
        }
        throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换 Provider");
    }

    private String decryptUserApiKey(String encryptedUserKey) {
        if (encryptedUserKey == null || encryptedUserKey.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.decrypt(encryptedUserKey);
        } catch (BusinessException exception) {
            log.warn("Failed to decrypt user API key, fallback to system default");
            return null;
        }
    }

    private LlmProviderResponse toResponse(LlmProviderConfig providerConfig) {
        return new LlmProviderResponse(
            providerConfig.getProviderKey(),
            providerConfig.getDisplayName(),
            parseModels(providerConfig.getAvailableModels()),
            providerConfig.getEnabled()
        );
    }

    private List<String> parseModels(String availableModels) {
        if (availableModels == null || availableModels.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(availableModels, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("Provider 配置格式错误");
        }
    }
}
