package com.prelude.identity;

import com.prelude.identity.application.OAuthLoginService;
import com.prelude.identity.application.PendingOAuthBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(session.getAttribute(OAuthLoginService.PENDING_ATTRIBUTE)).isNull();
    }
}
