package com.prelude.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.OAuthBinding;
import com.prelude.identity.OAuthBindingMapper;
import jakarta.servlet.http.HttpSession;
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

    private final AccountMapper accountMapper;
    private final OAuthBindingMapper oauthBindingMapper;

    @Transactional(rollbackFor = Exception.class)
    public AccountPrincipal resolveLogin(
        String provider,
        String providerSubject,
        String verifiedEmail,
        HttpSession session
    ) {
        Account boundAccount = findBoundAccount(provider, providerSubject);
        if (boundAccount != null) {
            completePendingThroughBoundAccount(provider, boundAccount, session);
            return new AccountPrincipal(boundAccount.getId(), boundAccount.getUsername());
        }

        if (verifiedEmail != null) {
            Account emailAccount = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getEmail, verifiedEmail)
                .last("LIMIT 1"));
            if (emailAccount != null) {
                session.setAttribute(PENDING_ATTRIBUTE, new PendingOAuthBinding(provider, providerSubject, verifiedEmail));
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
    private void completePendingThroughBoundAccount(String provider, Account boundAccount, HttpSession session) {
        if (!(session.getAttribute(PENDING_ATTRIBUTE) instanceof PendingOAuthBinding pending)
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
            // The pending identity can never bind to this account; drop the dead intent.
            session.removeAttribute(PENDING_ATTRIBUTE);
            log.warn("Pending binding for account {} conflicts with existing bindings; intent cleared",
                boundAccount.getId());
        }
    }

    private Account findBoundAccount(String provider, String providerSubject) {
        OAuthBinding binding = oauthBindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
            .eq(OAuthBinding::getProvider, provider)
            .eq(OAuthBinding::getProviderSubject, providerSubject)
            .last("LIMIT 1"));
        if (binding == null) {
            return null;
        }
        return accountMapper.selectById(binding.getAccountId());
    }

    private Account createOAuthAccount(String provider, String providerSubject, String verifiedEmail) {
        Account account = new Account();
        account.setUsername(generateUsername(provider, providerSubject));
        account.setEmail(verifiedEmail);
        account.setRevision(0L);
        accountMapper.insert(account);
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
            oauthBindingMapper.insert(binding);
        } catch (DuplicateKeyException duplicate) {
            if (!isExactExistingBinding(provider, providerSubject, accountId)) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "oauth_binding_conflict", "该外部账号已绑定其他账户");
            }
        }
    }

    private boolean isExactExistingBinding(String provider, String providerSubject, Long accountId) {
        OAuthBinding byIdentity = oauthBindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
            .eq(OAuthBinding::getProvider, provider)
            .eq(OAuthBinding::getProviderSubject, providerSubject)
            .last("LIMIT 1"));
        OAuthBinding byAccountProvider = oauthBindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
            .eq(OAuthBinding::getAccountId, accountId)
            .eq(OAuthBinding::getProvider, provider)
            .last("LIMIT 1"));
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
        while (accountMapper.selectCount(new LambdaQueryWrapper<Account>()
            .eq(Account::getUsername, candidate)) > 0) {
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
