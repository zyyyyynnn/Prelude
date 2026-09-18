package com.prelude.identity;

import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.OAuthLoginService;
import com.prelude.test.AccountFixtures;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final OAuthLoginService oauthLoginService = mock(OAuthLoginService.class);
    private final PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final AuthenticationService authenticationService =
        new AuthenticationService(accountMapper, oauthLoginService, passwordEncoder);
    private final org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();

    private final Account account = new Account();

    @BeforeEach
    void prepareAccount() {
        account.setId(7L);
        account.setUsername("candidate");
        account.setPasswordHash(passwordEncoder.encode("correct-horse"));
        when(accountMapper.insert(any(Account.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Account.class).setId(7L);
            return 1;
        });
        when(accountMapper.selectOne(any())).thenReturn(account);
    }

    @Test
    void registrationStoresAnArgon2idPasswordHash() {
        when(accountMapper.selectCount(any())).thenReturn(0L);

        authenticationService.register(AccountFixtures.registerRequest("candidate", "correct-horse"));

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountMapper).insert(created.capture());
        assertThat(created.getValue().getPasswordHash()).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("correct-horse", created.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", created.getValue().getPasswordHash())).isFalse();
    }

    @Test
    void correctPasswordAuthenticatesAndWrongPasswordIsRejected() {
        AccountPrincipal principal = authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), null, session);

        assertThat(principal.accountId()).isEqualTo(account.getId());
        ExceptionFixtures.assertBusinessException(
            () -> authenticationService.login(AccountFixtures.loginRequest("candidate", "wrong-password"), null, session),
            "invalid_credentials");
    }

    @Test
    void oauthOnlyAccountsWithoutPasswordHashCannotPasswordLogin() {
        account.setPasswordHash(null);

        ExceptionFixtures.assertBusinessException(
            () -> authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), null, session),
            "invalid_credentials");
    }

    @Test
    void pendingOAuthBindingMatchingTheAccountEmailIsCompletedOnPasswordLogin() {
        account.setEmail("owner@example.com");

        var pending = AccountFixtures.pendingOAuthBinding("google", "subject-1", "OWNER@example.com");
        session.setAttribute(AccountFixtures.PENDING_ATTRIBUTE, pending);
        AccountPrincipal principal = authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), pending, session);

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
        authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), pending, session);

        verify(oauthLoginService, never()).createBindingExact(any(), any(), any());
        assertThat(session.getAttribute(AccountFixtures.PENDING_ATTRIBUTE)).isEqualTo(pending);
    }

    @Test
    void pendingOAuthBindingForADifferentAccountIsNotBound() {
        account.setEmail("owner@example.com");

        var pending = AccountFixtures.pendingOAuthBinding("google", "subject-1", "other@example.com");
        authenticationService.login(AccountFixtures.loginRequest("candidate", "correct-horse"), pending, session);

        verify(oauthLoginService, never()).createBindingExact(any(), any(), any());
    }
}
