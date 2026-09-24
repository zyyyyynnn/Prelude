package com.prelude.llm;

import com.prelude.llm.application.port.ProviderCredentialStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves and decrypts an account-scoped provider credential. Execution hot
 * paths depend on this instead of the full profile configuration service.
 */
@Component
@RequiredArgsConstructor
class ProviderCredentialResolver {

    private final ProviderCredentialStore credentialStore;
    private final ProviderSecretCipher secretCipher;

    String resolve(Long accountId, Long credentialId) {
        String encryptedKey = credentialStore.findOwnedEncryptedKey(accountId, credentialId);
        return encryptedKey == null ? null : secretCipher.decrypt(encryptedKey);
    }
}
