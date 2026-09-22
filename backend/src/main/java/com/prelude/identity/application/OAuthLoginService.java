package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.application.port.HttpSessionAccess;
import com.prelude.identity.api.port.OAuthBindingRepository;
import com.prelude.identity.domain.Account;
import com.prelude.identity.domain.OAuthBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * OAuth binding policy: provider + provider subject is the identity truth,
 * verified email is only used for account discovery, never for silent merge.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    public static final String PENDING_ATTRIBUTE = PendingOAuthBinding.class.getName();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final OAuthBindingRepository bindings;

    @Transactional(rollbackFor = Exception.class)
    public AccountPrincipal resolveLogin(
        String provider,
        String providerSubject,
        String verifiedEmail,
        HttpSessionAccess session
    ) {
        Account boundAccount = findBoundAccount(provider, providerSubject);
        if (boundAccount != null) {
            completePendingThroughBoundAccount(provider, boundAccount, session);
            return new AccountPrincipal(boundAccount.getId(), boundAccount.getUsername());
        }

        if (verifiedEmail != null) {
            Account emailAccount = accounts.findByEmail(verifiedEmail);
            if (emailAccount != null) {
                session.attribute(PENDING_ATTRIBUTE, new PendingOAuthBinding(provider, providerSubject, verifiedEmail));
                log.info("OAuth {} identity matches existing account email; password re-authentication required", provider);
                return null;
            }
        }

        Account account = createOAuthAccount(provider, providerSubject, verifiedEmail);
        createBindingExact(provider, providerSubject, account.getId());
        return new AccountPrincipal(account.getId(), account.getUsername());
    }

    /**
     * Logging in through an already-bound identity proves that account's
     * ownership; if the session holds a pending binding for that same account's
     * verified email, it completes here (e.g. an OAuth-only account without a
     * password re-authenticating through its existing provider).
     */
    private void completePendingThroughBoundAccount(String provider, Account boundAccount, HttpSessionAccess session) {
        if (!(session.attribute(PENDING_ATTRIBUTE) instanceof PendingOAuthBinding pending)
            || boundAccount.getEmail() == null
            || !pending.verifiedEmail().equalsIgnoreCase(boundAccount.getEmail())) {
            return;
        }
        try {
            createBindingExact(pending.provider(), pending.providerSubject(), boundAccount.getId());
            session.removeAttribute(PENDING_ATTRIBUTE);
            log.info("Pending {} binding completed through existing {} identity for account {}",
                pending.provider(), provider, boundAccount.getId());
        } catch (BusinessException conflict) {
            // The pending intent can never complete; drop it, but surface the conflict
            // instead of masking the failed binding as a successful login.
            session.removeAttribute(PENDING_ATTRIBUTE);
            throw conflict;
        }
    }

    private Account findBoundAccount(String provider, String providerSubject) {
        OAuthBinding binding = bindings.findByProviderAndSubject(provider, providerSubject);
        if (binding == null) {
            return null;
        }
        return accounts.findById(binding.getAccountId());
    }

    private Account createOAuthAccount(String provider, String providerSubject, String verifiedEmail) {
        Account account = new Account();
        account.setUsername(generateUsername(provider, providerSubject));
        account.setEmail(verifiedEmail);
        account.setRevision(0L);
        accounts.add(account);
        return account;
    }

    /**
     * Inserts the binding. After a unique-constraint race the database state is
     * re-read: only the exact expected (provider, subject, account) mapping is
     * an idempotent success; anything else is a stable conflict.
     */
    public void createBindingExact(String provider, String providerSubject, Long accountId) {
        OAuthBinding binding = new OAuthBinding();
        binding.setAccountId(accountId);
        binding.setProvider(provider);
        binding.setProviderSubject(providerSubject);
        try {
            bindings.add(binding);
        } catch (DuplicateKeyException duplicate) {
            if (!isExactExistingBinding(provider, providerSubject, accountId)) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "oauth_binding_conflict", "该外部账号已绑定其他账户");
            }
        }
    }

    private boolean isExactExistingBinding(String provider, String providerSubject, Long accountId) {
        OAuthBinding byIdentity = bindings.findByProviderAndSubject(provider, providerSubject);
        OAuthBinding byAccountProvider = bindings.findByAccountAndProvider(accountId, provider);
        return byIdentity != null
            && accountId.equals(byIdentity.getAccountId())
            && byAccountProvider != null
            && providerSubject.equals(byAccountProvider.getProviderSubject());
    }

    private String generateUsername(String provider, String providerSubject) {
        String sanitized = (provider + "_" + providerSubject).replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        String candidate = sanitized;
        while (accounts.isUsernameTaken(candidate)) {
            candidate = sanitized + "-" + HexFormat.of().formatHex(randomSuffix());
        }
        return candidate;
    }

    private byte[] randomSuffix() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
