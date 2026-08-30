package com.prelude.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevAccountInitializerTest {

    private final AccountMapper accountMapper = org.mockito.Mockito.mock(AccountMapper.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final DevAccountInitializer initializer =
        new DevAccountInitializer(accountMapper, passwordEncoder);

    @Test
    void createsTheDemoAccountWhenItIsMissing() throws Exception {
        when(accountMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(DevAccountInitializer.PASSWORD)).thenReturn("encoded-password");

        initializer.run(null);

        verify(accountMapper).insert(any(Account.class));
        verify(accountMapper, never()).updateById(any(Account.class));
    }

    @Test
    void restoresTheExpectedPasswordForAnExistingDemoAccount() throws Exception {
        Account account = new Account();
        account.setId(1L);
        account.setUsername(DevAccountInitializer.USERNAME);
        account.setPasswordHash("stale-password");
        when(accountMapper.selectOne(any())).thenReturn(account);
        when(passwordEncoder.matches(DevAccountInitializer.PASSWORD, "stale-password"))
            .thenReturn(false);
        when(passwordEncoder.encode(DevAccountInitializer.PASSWORD)).thenReturn("encoded-password");

        initializer.run(null);

        verify(accountMapper).updateById(account);
        org.assertj.core.api.Assertions.assertThat(account.getPasswordHash()).isEqualTo("encoded-password");
    }
}
