package com.prelude.identity;

import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.api.port.OAuthBindingRepository;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.application.PendingOAuthBinding;
import com.prelude.identity.domain.Account;
import com.prelude.identity.domain.OAuthBinding;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OAuthBindingRepository bindings = mock(OAuthBindingRepository.class);
    private final OAuthLoginService oauthLoginService = new OAuthLoginService(accounts, bindings);

    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void allowAccountInserts() {
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Account.class).setId(9L);
            return null;
        }).when(accounts).add(any());
    }

    @Test
    void anExistingBindingLogsIntoTheBoundAccount() {
        when(bindings.findByProviderAndSubject("google", "subject-1")).thenReturn(binding("google", "subject-1", 5L));
        when(accounts.findById(5L)).thenReturn(account(5L, "owner", null));

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-1", "owner@example.com", SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(5L);
        verify(accounts, never()).add(any());
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void aVerifiedEmailCollisionRequiresPasswordReauthenticationInsteadOfSilentMerge() {
        when(bindings.findByProviderAndSubject("google", "subject-1")).thenReturn(null);
        when(accounts.findByEmail("owner@example.com")).thenReturn(account(5L, "owner", "owner@example.com"));

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-1", "owner@example.com", SessionFixtures.sessionAccess(session));

        assertThat(principal).isNull();
        verify(accounts, never()).add(any());
        PendingOAuthBinding pending =
            (PendingOAuthBinding) session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE);
        assertThat(pending).isEqualTo(new PendingOAuthBinding("google", "subject-1", "owner@example.com"));
    }

    @Test
    void anUnboundIdentityWithoutEmailCollisionCreatesAccountAndBinding() {
        when(bindings.findByProviderAndSubject("github", "subject-2")).thenReturn(null);
        when(accounts.findByEmail("new@example.com")).thenReturn(null);
        when(accounts.isUsernameTaken(any())).thenReturn(false);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("github", "subject-2", "new@example.com", SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(9L);
        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accounts).add(created.capture());
        assertThat(created.getValue().getUsername()).startsWith("github_subject-2");
        assertThat(created.getValue().getPasswordHash()).isNull();
        // The verified email persists so a later provider with the same verified
        // address enters discovery + re-auth instead of creating a second account.
        assertThat(created.getValue().getEmail()).isEqualTo("new@example.com");
        ArgumentCaptor<OAuthBinding> binding = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(bindings).add(binding.capture());
        assertThat(binding.getValue().getAccountId()).isEqualTo(9L);
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void anUnverifiedEmailNeverTriggersAccountDiscovery() {
        when(bindings.findByProviderAndSubject("google", "subject-3")).thenReturn(null);
        when(accounts.isUsernameTaken(any())).thenReturn(false);

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-3", null, SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(9L);
        verify(accounts, never()).findByEmail(any());
        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accounts).add(created.capture());
        assertThat(created.getValue().getEmail()).isNull();
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void anOAuthOnlyAccountReauthenticatesThroughItsExistingBoundProvider() {
        // A pending GitHub binding for a verified email owned by a Google-bound,
        // password-less account: logging in through Google completes the binding.
        PendingOAuthBinding pending = new PendingOAuthBinding("github", "subject-gh", "owner@example.com");
        session.setAttribute(OAuthLoginService.PENDING_ATTRIBUTE, pending);
        when(bindings.findByProviderAndSubject("google", "subject-goog")).thenReturn(binding("google", "subject-goog", 5L));
        when(accounts.findById(5L)).thenReturn(account(5L, "owner", "owner@example.com"));

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("google", "subject-goog", null, SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(5L);
        ArgumentCaptor<OAuthBinding> created = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(bindings).add(created.capture());
        assertThat(created.getValue().getProvider()).isEqualTo("github");
        assertThat(created.getValue().getProviderSubject()).isEqualTo("subject-gh");
        assertThat(created.getValue().getAccountId()).isEqualTo(5L);
        verify(accounts, never()).add(any());
        // One-shot: the pending intent is consumed with its completion.
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void aConflictingPendingCompletionSurfacesTheConflictInsteadOfSilentSuccess() {
        PendingOAuthBinding pending = new PendingOAuthBinding("github", "subject-gh", "owner@example.com");
        session.setAttribute(OAuthLoginService.PENDING_ATTRIBUTE, pending);
        when(bindings.findByProviderAndSubject("google", "subject-goog")).thenReturn(binding("google", "subject-goog", 5L));
        when(bindings.findByProviderAndSubject("github", "subject-gh")).thenReturn(binding("github", "subject-gh", 8L));
        when(accounts.findById(5L)).thenReturn(account(5L, "owner", "owner@example.com"));
        stubBindingConflict();

        ExceptionFixtures.assertBusinessException(() ->
            oauthLoginService.resolveLogin("google", "subject-goog", null, SessionFixtures.sessionAccess(session)),
            "oauth_binding_conflict");

        // The dead intent is cleared, but the failed binding was never created.
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
        ArgumentCaptor<OAuthBinding> attempted = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(bindings).add(attempted.capture());
        assertThat(attempted.getValue().getProvider()).isEqualTo("github");
        assertThat(attempted.getValue().getAccountId()).isEqualTo(5L);
    }

    @Test
    void aConflictingDuplicateBindingIsNotTreatedAsIdempotent() {
        when(bindings.findByProviderAndSubject("github", "subject-x")).thenReturn(null, binding("github", "subject-x", 8L));
        when(accounts.isUsernameTaken(any())).thenReturn(false);
        stubBindingConflict();

        ExceptionFixtures.assertBusinessException(() ->
            oauthLoginService.resolveLogin("github", "subject-x", null, SessionFixtures.sessionAccess(session)),
            "oauth_binding_conflict");
    }

    @Test
    void anExactDuplicateBindingAfterARaceIsAnIdempotentSuccess() {
        OAuthBinding existing = binding("github", "subject-x", 9L);
        when(bindings.findByProviderAndSubject("github", "subject-x")).thenReturn(null, existing);
        when(bindings.findByAccountAndProvider(9L, "github")).thenReturn(existing);
        when(accounts.isUsernameTaken(any())).thenReturn(false);
        stubBindingConflict();

        AccountPrincipal principal =
            oauthLoginService.resolveLogin("github", "subject-x", null, SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(9L);
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }

    private void stubBindingConflict() {
        doThrow(new DuplicateKeyException("unique violation")).when(bindings).add(any());
    }

    private static OAuthBinding binding(String provider, String subject, Long accountId) {
        OAuthBinding binding = new OAuthBinding();
        binding.setProvider(provider);
        binding.setProviderSubject(subject);
        binding.setAccountId(accountId);
        return binding;
    }

    private static Account account(Long id, String username, String email) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        account.setEmail(email);
        return account;
    }
}
