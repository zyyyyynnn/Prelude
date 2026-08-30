package com.prelude.identity;

import com.prelude.BusinessException;
import com.prelude.identity.api.LoginRequest;
import com.prelude.identity.api.RegisterRequest;
import com.prelude.identity.application.AuthenticationService;
import com.prelude.identity.application.PendingOAuthBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final OAuthBindingMapper oauthBindingMapper = mock(OAuthBindingMapper.class);
    private final PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final AuthenticationService authenticationService =
        new AuthenticationService(accountMapper, oauthBindingMapper, passwordEncoder);

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

        authenticationService.register(command());

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountMapper).insert(created.capture());
        assertThat(created.getValue().getPasswordHash()).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("correct-horse", created.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", created.getValue().getPasswordHash())).isFalse();
    }

    @Test
    void correctPasswordAuthenticatesAndWrongPasswordIsRejected() {
        AccountPrincipal principal = authenticationService.login(request("correct-horse"), null);

        assertThat(principal.accountId()).isEqualTo(account.getId());
        assertThatThrownBy(() -> authenticationService.login(request("wrong-password"), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "invalid_credentials");
    }

    @Test
    void oauthOnlyAccountsWithoutPasswordHashCannotPasswordLogin() {
        account.setPasswordHash(null);

        assertThatThrownBy(() -> authenticationService.login(request("correct-horse"), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "invalid_credentials");
    }

    @Test
    void pendingOAuthBindingMatchingTheAccountEmailIsCompletedOnPasswordLogin() {
        account.setEmail("owner@example.com");

        PendingOAuthBinding pending = new PendingOAuthBinding("google", "subject-1", "OWNER@example.com");
        AccountPrincipal principal = authenticationService.login(request("correct-horse"), pending);

        assertThat(principal.accountId()).isEqualTo(account.getId());
        ArgumentCaptor<OAuthBinding> binding = ArgumentCaptor.forClass(OAuthBinding.class);
        verify(oauthBindingMapper).insert(binding.capture());
        assertThat(binding.getValue().getAccountId()).isEqualTo(account.getId());
        assertThat(binding.getValue().getProvider()).isEqualTo("google");
        assertThat(binding.getValue().getProviderSubject()).isEqualTo("subject-1");
    }

    @Test
    void pendingOAuthBindingForADifferentAccountIsNotBound() {
        account.setEmail("owner@example.com");

        PendingOAuthBinding pending = new PendingOAuthBinding("google", "subject-1", "other@example.com");
        authenticationService.login(request("correct-horse"), pending);

        verify(oauthBindingMapper, never()).insert(any(OAuthBinding.class));
    }

    private RegisterRequest command() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("candidate");
        request.setPassword("correct-horse");
        return request;
    }

    private LoginRequest request(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("candidate");
        request.setPassword(password);
        return request;
    }
}
