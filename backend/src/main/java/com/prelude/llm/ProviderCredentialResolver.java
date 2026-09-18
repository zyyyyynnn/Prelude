package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.persistence.ProviderCredential;
import com.prelude.llm.persistence.ProviderCredentialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves and decrypts an account-scoped provider credential. Execution hot
 * paths depend on this instead of the full profile configuration service.
 */
@Component
@RequiredArgsConstructor
class ProviderCredentialResolver {

    private final ProviderCredentialMapper credentialMapper;
    private final ProviderSecretCipher secretCipher;

    String resolve(Long accountId, Long credentialId) {
        if (credentialId == null) {
            return null;
        }
        ProviderCredential credential = credentialMapper.selectById(credentialId);
        if (credential == null || !accountId.equals(credential.getAccountId())) {
            throw BusinessException.badRequest("模型凭据不可用，请重新配置");
        }
        return secretCipher.decrypt(credential.getApiKeyEncrypted());
    }
}
