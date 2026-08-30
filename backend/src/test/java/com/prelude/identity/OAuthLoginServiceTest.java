package com.prelude.identity;

import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.application.PendingOAuthBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final OAuthBindingMapper oauthBindingMapper = mock(OAuthBindingMapper.class);
    private final OAuthLoginService oauthLoginService = new OAuthLoginService(accountMapper, oauthBindingMapper);

    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void allowAccountInserts() {
        when(accountMapper.insert(any(Account.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Account.class).setId(9L);
            return 1;
        });
    }

    @Test
    void anExistingBindingLogsIntoTheBoundAccount() {
        OAuthBinding binding = new OAuthBinding();
        binding.setAccountId(5L);
        binding.setProvider("google");
        binding.setProviderSubject("subject-1");
        when(oauthBindingMapper.selectOne(any())).thenReturn(binding);
        Account boundAccount = new Account();
        boundAccount.setId(5L);
        boundAccount.setUsername("owner");
        when(accountMapper.selectById(5L)).thenReturn(boundAccount);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-1", "owner@example.com", session);

        assertThat(principal.accountId()).isEqualTo(5L);
        verify(accountMapper, never()).insert(any(Account.class));
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void aVerifiedEmailCollisionRequiresPasswordReauthenticationInsteadOfSilentMerge() {
        when(oauthBindingMapper.selectOne(any())).thenReturn(null);
        Account existing = new Account();
        existing.setId(5L);
        existing.setUsername("owner");
        existing.setEmail("owner@example.com");
        when(accountMapper.selectOne(any())).thenReturn(existing);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-1", "owner@example.com", session);

        assertThat(principal).isNull();
        verify(accountMapper, never()).insert(any(Account.class));
        PendingOAuthBinding pending =
            (PendingOAuthBinding) session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE);
        assertThat(pending).isEqualTo(new PendingOAuthBinding("google", "subject-1", "owner@example.com"));
    }

    @Test
    void anUnboundIdentityWithoutEmailCollisionCreatesAccountAndBinding() {
        when(oauthBindingMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectCount(any())).thenReturn(0L);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("github", "subject-2", "new@example.com", session);

        assertThat(principal.accountId()).isEqualTo(9L);
        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountMapper).insert(created.capture());
        assertThat(created.getValue().getUsername()).startsWith("github_subject-2");
        assertThat(created.getValue().getPasswordHash()).isNull();
        // The verified email persists so a later provider with the same verified
        // address enters discovery + re-auth instead of creating a second account.
        assertThat(created.getValue().getEmail()).isEqualTo("new@example.com");
        ArgumentCaptor<OAuthBinding> binding = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(oauthBindingMapper).insert(binding.capture());
        assertThat(binding.getValue().getAccountId()).isEqualTo(9L);
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void anUnverifiedEmailNeverTriggersAccountDiscovery() {
        when(oauthBindingMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectCount(any())).thenReturn(0L);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-3", null, session);

        assertThat(principal.accountId()).isEqualTo(9L);
        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountMapper).insert(created.capture());
        assertThat(created.getValue().getEmail()).isNull();
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void anOAuthOnlyAccountReauthenticatesThroughItsExistingBoundProvider() {
        // A pending GitHub binding for a verified email owned by a Google-bound,
        // password-less account: logging in through Google completes the binding.
        PendingOAuthBinding pending = new PendingOAuthBinding("github", "subject-gh", "owner@example.com");
        session.setAttribute(OAuthLoginService.PENDING_ATTRIBUTE, pending);
        OAuthBinding googleBinding = new OAuthBinding();
        googleBinding.setAccountId(5L);
        googleBinding.setProvider("google");
        googleBinding.setProviderSubject("subject-goog");
        when(oauthBindingMapper.selectOne(any())).thenReturn(googleBinding);
        Account oauthOnlyAccount = new Account();
        oauthOnlyAccount.setId(5L);
        oauthOnlyAccount.setUsername("owner");
        oauthOnlyAccount.setEmail("owner@example.com");
        oauthOnlyAccount.setPasswordHash(null);
        when(accountMapper.selectById(5L)).thenReturn(oauthOnlyAccount);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-goog", null, session);

        assertThat(principal.accountId()).isEqualTo(5L);
        ArgumentCaptor<OAuthBinding> created = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(oauthBindingMapper).insert(created.capture());
        assertThat(created.getValue().getProvider()).isEqualTo("github");
        assertThat(created.getValue().getProviderSubject()).isEqualTo("subject-gh");
        assertThat(created.getValue().getAccountId()).isEqualTo(5L);
        verify(accountMapper, never()).insert(any(Account.class));
        // One-shot: the pending intent is consumed with its completion.
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void aConflictingPendingCompletionSurfacesTheConflictInsteadOfSilentSuccess() {
        PendingOAuthBinding pending = new PendingOAuthBinding("github", "subject-gh", "owner@example.com");
        session.setAttribute(OAuthLoginService.PENDING_ATTRIBUTE, pending);
        OAuthBinding googleBinding = new OAuthBinding();
        googleBinding.setAccountId(5L);
        googleBinding.setProvider("google");
        googleBinding.setProviderSubject("subject-goog");
        OAuthBinding conflictingBinding = new OAuthBinding();
        conflictingBinding.setAccountId(8L);
        conflictingBinding.setProvider("github");
        conflictingBinding.setProviderSubject("subject-gh");
        when(oauthBindingMapper.selectOne(any()))
            .thenReturn(googleBinding, conflictingBinding, conflictingBinding);
        when(accountMapper.selectById(5L)).thenAnswer(invocation -> {
            Account boundAccount = new Account();
            boundAccount.setId(5L);
            boundAccount.setUsername("owner");
            boundAccount.setEmail("owner@example.com");
            return boundAccount;
        });
        when(oauthBindingMapper.insert(any(OAuthBinding.class)))
            .thenThrow(new DuplicateKeyException("unique violation"));

        assertThatThrownBy(() ->
            oauthLoginService.resolveLogin("google", "subject-goog", null, session))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "oauth_binding_conflict");

        // The dead intent is cleared, but the failed binding was never created.
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
        ArgumentCaptor<OAuthBinding> attempted = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(oauthBindingMapper).insert(attempted.capture());
        assertThat(attempted.getValue().getProvider()).isEqualTo("github");
        assertThat(attempted.getValue().getProviderSubject()).isEqualTo("subject-gh");
        assertThat(attempted.getValue().getAccountId()).isEqualTo(5L);
    }

    @Test
    void aConflictingDuplicateBindingIsNotTreatedAsIdempotent() {
        when(oauthBindingMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        OAuthBinding conflictingBinding = new OAuthBinding();
        conflictingBinding.setAccountId(8L);
        conflictingBinding.setProvider("github");
        conflictingBinding.setProviderSubject("subject-x");
        when(oauthBindingMapper.selectOne(any())).thenReturn(null, conflictingBinding);
        when(oauthBindingMapper.insert(any(OAuthBinding.class)))
            .thenThrow(new DuplicateKeyException("unique violation"));

        assertThatThrownBy(() ->
            oauthLoginService.resolveLogin("github", "subject-x", null, session))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "oauth_binding_conflict");
    }

    @Test
    void anExactDuplicateBindingAfterARaceIsAnIdempotentSuccess() {
        when(oauthBindingMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        OAuthBinding existingBinding = new OAuthBinding();
        existingBinding.setAccountId(9L);
        existingBinding.setProvider("github");
        existingBinding.setProviderSubject("subject-x");
        when(oauthBindingMapper.selectOne(any())).thenReturn(null, existingBinding, existingBinding);
        when(oauthBindingMapper.insert(any(OAuthBinding.class)))
            .thenThrow(new DuplicateKeyException("unique violation"));

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("github", "subject-x", null, session);

        assertThat(principal.accountId()).isEqualTo(9L);
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }
}
