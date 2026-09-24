package com.prelude.llm.application.port;

import java.util.Optional;

/**
 * Provider-credential row access. Speaks in the credential's own fields rather than
 * the persistence row type, so no use case names the persistence package.
 */
public interface ProviderCredentialStore {

    /** The scope a deployment-wide credential is stored under. */
    String SYSTEM_SCOPE = "system";

    /**
     * The encrypted API key of the credential, but only when it really belongs to the
     * account asking for it.
     *
     * @throws com.prelude.BusinessException when the credential is missing or owned by another account
     */
    String findOwnedEncryptedKey(Long accountId, Long credentialId);

    /** The credential row, or empty when no credential carries that id. */
    Optional<CredentialRow> findById(Long credentialId);

    /** Inserts a credential and reports the row it wrote. */
    CredentialRow insert(CredentialRow credential);

    /** The credential row as the service sees it. */
    record CredentialRow(
        Long id,
        Long accountId,
        String provider,
        String scopeKey,
        String apiKeyEncrypted
    ) {
    }
}
