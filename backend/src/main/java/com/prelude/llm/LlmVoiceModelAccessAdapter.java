package com.prelude.llm;

import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.settings.AesGcmEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LlmVoiceModelAccessAdapter implements VoiceModelAccessPort {

    private final AccountMapper accountMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final String systemBaseUrl;
    private final String systemApiKey;

    public LlmVoiceModelAccessAdapter(
        AccountMapper accountMapper,
        AesGcmEncryptor aesGcmEncryptor,
        @Value("${openai.base-url:https://api.openai.com/v1}") String systemBaseUrl,
        @Value("${openai.api-key:}") String systemApiKey
    ) {
        this.accountMapper = accountMapper;
        this.aesGcmEncryptor = aesGcmEncryptor;
        this.systemBaseUrl = systemBaseUrl;
        this.systemApiKey = systemApiKey;
    }

    @Override
    public VoiceModelAccess resolveForAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalStateException("账户未登录");
        }
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalStateException("账户不存在");
        }

        String baseUrl = systemBaseUrl;
        String apiKey = systemApiKey;
        String providerKey = account.getLlmProvider();
        if (CustomLlmProtocol.OPENAI_RESPONSES.providerKey().equals(providerKey)
            || CustomLlmProtocol.OPENAI_CHAT_COMPLETIONS.providerKey().equals(providerKey)) {
            CustomLlmProtocol protocol = CustomLlmProtocol.require(providerKey);
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(account.getLlmBaseUrl(), protocol);
            apiKey = decryptAccountKey(account.getLlmApiKeyEncrypted());
        }
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException("缺少可用的 OpenAI 语音访问密钥");
        }
        return new VoiceModelAccess(baseUrl, apiKey);
    }

    private String decryptAccountKey(String encryptedApiKey) {
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
