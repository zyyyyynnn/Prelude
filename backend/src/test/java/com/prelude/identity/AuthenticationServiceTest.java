package com.prelude.identity;

import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.api.port.AccountRepository;
import com.prelude.identity.domain.Account;
import com.prelude.test.AccountFixtures;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OAuthLoginService oauthLoginService = mock(OAuthLoginService.class);
    private final PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final AuthenticationService authenticationService =
        new AuthenticationService(accounts, oauthLoginService, passwordEncoder);
    private final MockHttpSession session = new MockHttpSession();

    private final Account account = new Account();

    @BeforeEach
    void prepareAccount() {
        account.setId(7L);
        account.setUsername("candidate");
        account.setPasswordHash(passwordEncoder.encode("correct-horse"));
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Account.class).setId(7L);
            return null;
        }).when(accounts).add(any());
        when(accounts.findByUsername("candidate")).thenReturn(account);
    }

    @Test
    void registrationStoresAnArgon2idPasswordHash() {
        when(accounts.isUsernameTaken("candidate")).thenReturn(false);

        authenticationService.register(AccountFixtures.registerRequest("candidate", "correct-horse"));

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accounts).add(created.capture());
        assertThat(created.getValue().getPasswordHash()).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("correct-horse", created.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", created.getValue().getPasswordHash())).isFalse();
    }

    @Test
    void registrationRejectsATakenUsernameBeforeWriting() {
        when(accounts.isUsernameTaken("candidate")).thenReturn(true);

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> authenticationService.register(AccountFixtures.registerRequest("candidate", "correct-horse")),
            "用户名已存在");
        verify(accounts, never()).add(any());
    }

    @Test
    void correctPasswordAuthenticatesAndWrongPasswordIsRejected() {
        AccountPrincipal principal = authenticationService.login(
            AccountFixtures.loginRequest("candidate", "correct-horse"), null, SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(account.getId());
        ExceptionFixtures.assertBusinessException(
            () -> authenticationService.login(AccountFixtures.loginRequest("candidate", "wrong-password"), null, SessionFixtures.sessionAccess(session)),
            "invalid_credentials");
    }

    @Test
    void oauthOnlyAccountsWithoutPasswordHashCannotPasswordLogin() {
        account.setPasswordHash(null);

        ExceptionFixtures.assertBusinessException(
            () -> authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), null, SessionFixtures.sessionAccess(session)),
            "invalid_credentials");
    }

    @Test
    void pendingOAuthBindingMatchingTheAccountEmailIsCompletedOnPasswordLogin() {
        account.setEmail("owner@example.com");

        var pending = AccountFixtures.pendingOAuthBinding("google", "subject-1", "OWNER@example.com");
        session.setAttribute(AccountFixtures.PENDING_ATTRIBUTE, pending);
        AccountPrincipal principal = authenticationService.login(
            AccountFixtures.loginRequest("candidate", "correct-horse"), pending, SessionFixtures.sessionAccess(session));

        assertThat(principal.accountId()).isEqualTo(account.getId());
        verify(oauthLoginService).createBindingExact("google", "subject-1", account.getId());
        // One-shot: the completed intent must not survive the rotated session.
        assertThat(session.getAttribute(AccountFixtures.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void pendingIntentSurvivesWhenTheProvenAccountDoesNotMatch() {
        account.setEmail("owner@example.com");

        var pending = AccountFixtures.pendingOAuthBinding("google", "subject-1", "other@example.com");
        session.setAttribute(AccountFixtures.PENDING_ATTRIBUTE, pending);
        authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), pending, SessionFixtures.sessionAccess(session));

        verify(oauthLoginService, never()).createBindingExact(any(), any(), any());
        assertThat(session.getAttribute(AccountFixtures.PENDING_ATTRIBUTE)).isEqualTo(pending);
    }

    @Test
    void pendingOAuthBindingForADifferentAccountIsNotBound() {
        account.setEmail("owner@example.com");

        var pending = AccountFixtures.pendingOAuthBinding("google", "subject-1", "other@example.com");
        authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), pending, SessionFixtures.sessionAccess(session));

        verify(oauthLoginService, never()).createBindingExact(any(), any(), any());
    }
}
