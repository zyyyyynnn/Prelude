package com.interview.platform.llm;

import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.shared.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LlmVoiceModelAccessAdapter implements VoiceModelAccessPort {

    private final UserMapper userMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final String systemBaseUrl;
    private final String systemApiKey;

    public LlmVoiceModelAccessAdapter(
        UserMapper userMapper,
        AesGcmEncryptor aesGcmEncryptor,
        @Value("${openai.base-url:https://api.openai.com/v1}") String systemBaseUrl,
        @Value("${openai.api-key:}") String systemApiKey
    ) {
        this.userMapper = userMapper;
        this.aesGcmEncryptor = aesGcmEncryptor;
        this.systemBaseUrl = systemBaseUrl;
        this.systemApiKey = systemApiKey;
    }

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

        String baseUrl = systemBaseUrl;
        String apiKey = systemApiKey;
        String providerKey = user.getLlmProvider();
        if (CustomLlmProtocol.OPENAI_RESPONSES.providerKey().equals(providerKey)
            || CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS.providerKey().equals(providerKey)) {
            CustomLlmProtocol protocol = CustomLlmProtocol.require(providerKey);
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(user.getLlmBaseUrl(), protocol);
            apiKey = decryptUserKey(user.getLlmApiKeyEncrypted());
        }
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException("缺少可用的 OpenAI 语音访问密钥");
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
            log.warn("Failed to decrypt selected OpenAI BYOK key");
            return null;
        }
    }
}
