package com.interview.platform.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.llm.persistence.LlmProviderConfig;
import com.interview.identity.domain.User;
import com.interview.platform.llm.persistence.LlmProviderConfigMapper;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.shared.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmVoiceModelAccessAdapter implements VoiceModelAccessPort {

    private final UserMapper userMapper;
    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final List<LlmProvider> providers;

    @Override
    public VoiceModelAccess resolveCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }

        String providerKey = user.getLlmProvider();
        if (providerKey == null || providerKey.isBlank()) {
            providerKey = "openai";
        }
        LlmProviderConfig providerConfig = llmProviderConfigMapper.selectOne(
            new LambdaQueryWrapper<LlmProviderConfig>()
                .eq(LlmProviderConfig::getProviderKey, providerKey)
                .eq(LlmProviderConfig::getEnabled, 1)
                .last("LIMIT 1")
        );
        String baseUrl = providerConfig != null && providerConfig.getBaseUrl() != null
            && !providerConfig.getBaseUrl().isBlank()
            ? providerConfig.getBaseUrl()
            : "https://api.openai.com/v1";

        String apiKey = decryptUserKey(user.getLlmApiKeyEncrypted());
        if (apiKey == null || apiKey.isBlank()) {
            String selectedProvider = providerKey;
            apiKey = providers.stream()
                .filter(provider -> provider.providerKey().equalsIgnoreCase(selectedProvider))
                .findFirst()
                .map(LlmProvider::systemApiKey)
                .orElse(null);
        }
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException("缺少可用的 OpenAI / Whisper 访问密钥");
        }
        return new VoiceModelAccess(baseUrl, apiKey);
    }

    private String decryptUserKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.decrypt(encryptedApiKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to decrypt user key, fallback to system configurations");
            return null;
        }
    }
}
