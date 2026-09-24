package com.prelude.llm.infrastructure;

import com.prelude.BusinessException;
import com.prelude.llm.application.port.ProviderCredentialStore;
import com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow;
import com.prelude.llm.infrastructure.persistence.ProviderCredentialEntity;
import com.prelude.llm.infrastructure.persistence.ProviderCredentialMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link ProviderCredentialStore}. */
@Repository
@RequiredArgsConstructor
public class MybatisProviderCredentialStore implements ProviderCredentialStore {

    private final ProviderCredentialMapper credentialMapper;

    @Override
    public String findOwnedEncryptedKey(Long accountId, Long credentialId) {
        if (credentialId == null) {
            return null;
        }
        ProviderCredentialEntity credential = credentialMapper.selectById(credentialId);
        if (credential == null || !accountId.equals(credential.getAccountId())) {
            throw BusinessException.badRequest("模型凭证不存在或不属于当前账户");
        }
        return credential.getApiKeyEncrypted();
    }

    @Override
    public Optional<CredentialRow> findById(Long credentialId) {
        ProviderCredentialEntity credential = credentialMapper.selectById(credentialId);
        return credential == null ? Optional.empty() : Optional.of(toRow(credential));
    }

    @Override
    public CredentialRow insert(CredentialRow row) {
        ProviderCredentialEntity credential = new ProviderCredentialEntity();
        credential.setAccountId(row.accountId());
        credential.setProvider(row.provider());
        credential.setScopeKey(row.scopeKey());
        credential.setApiKeyEncrypted(row.apiKeyEncrypted());
        credentialMapper.insert(credential);
        return toRow(credential);
    }

    private CredentialRow toRow(ProviderCredentialEntity credential) {
        return new CredentialRow(
            credential.getId(),
            credential.getAccountId(),
            credential.getProvider(),
            credential.getScopeKey(),
            credential.getApiKeyEncrypted()
        );
    }
}
