package com.prelude.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DevAccountInitializerTest {

    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder =
        org.mockito.Mockito.mock(BCryptPasswordEncoder.class);
    private final DevAccountInitializer initializer =
        new DevAccountInitializer(userMapper, passwordEncoder);

    @Test
    void createsTheDemoAccountWhenItIsMissing() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(DevAccountInitializer.PASSWORD)).thenReturn("encoded-password");

        initializer.run(null);

        verify(userMapper).insert(any(User.class));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void restoresTheExpectedPasswordForAnExistingDemoAccount() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername(DevAccountInitializer.USERNAME);
        user.setPassword("stale-password");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(DevAccountInitializer.PASSWORD, "stale-password"))
            .thenReturn(false);
        when(passwordEncoder.encode(DevAccountInitializer.PASSWORD)).thenReturn("encoded-password");

        initializer.run(null);

        verify(userMapper).updateById(user);
        org.assertj.core.api.Assertions.assertThat(user.getPassword()).isEqualTo("encoded-password");
    }
}
