package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.identity.api.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceAvatarTest {

    private static final String OLD_AVATAR = "/api/assets/5/content";
    private static final String NEW_AVATAR = "/api/assets/9/content";

    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final AvatarStoragePort avatarStoragePort = mock(AvatarStoragePort.class);
    private final ProfileService profileService = new ProfileService(
        accountMapper,
        mock(PasswordEncoder.class),
        currentAccount(7L),
        avatarStoragePort
    );

    private final Account account = new Account();

    @BeforeEach
    void prepareAccount() {
        account.setId(7L);
        account.setUsername("candidate");
        account.setRevision(5L);
        account.setAvatarUrl(OLD_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account);
    }

    @Test
    void conflictDiscardsOnlyTheNewAvatarAndKeepsTheOldReference() {
        when(avatarStoragePort.store(7L, "image/png", bytes())).thenReturn(NEW_AVATAR);
        when(accountMapper.updateProfileGuarded(anyLong(), anyString(), any(), any(), any(), any(),
            eq(5L), any())).thenReturn(0);

        assertThatThrownBy(() -> profileService.updateAvatar(avatarFile()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "revision_conflict");

        verify(avatarStoragePort).discard(7L, NEW_AVATAR);
        verify(avatarStoragePort, never()).discard(7L, OLD_AVATAR);
        assertThat(account.getAvatarUrl()).isEqualTo(OLD_AVATAR);
    }

    @Test
    void successCommitsTheNewReferenceThenCleansUpTheObsoleteAvatar() {
        when(avatarStoragePort.store(7L, "image/png", bytes())).thenReturn(NEW_AVATAR);
        when(accountMapper.updateProfileGuarded(anyLong(), anyString(), any(), any(), any(), any(),
            eq(5L), any())).thenReturn(1);
        Account reloaded = new Account();
        reloaded.setId(7L);
        reloaded.setUsername("candidate");
        reloaded.setRevision(6L);
        reloaded.setAvatarUrl(NEW_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account, reloaded);

        UserProfileResponse response = profileService.updateAvatar(avatarFile());

        assertThat(response.avatarUrl()).isEqualTo(NEW_AVATAR);
        verify(avatarStoragePort).discard(7L, OLD_AVATAR);
        verify(avatarStoragePort, never()).discard(7L, NEW_AVATAR);
    }

    @Test
    void obsoleteAvatarCleanupFailureDoesNotRollBackTheCommittedReference() {
        when(avatarStoragePort.store(7L, "image/png", bytes())).thenReturn(NEW_AVATAR);
        when(accountMapper.updateProfileGuarded(anyLong(), anyString(), any(), any(), any(), any(),
            eq(5L), any())).thenReturn(1);
        Account reloaded = new Account();
        reloaded.setId(7L);
        reloaded.setUsername("candidate");
        reloaded.setRevision(6L);
        reloaded.setAvatarUrl(NEW_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account, reloaded);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage down"))
            .when(avatarStoragePort).discard(7L, OLD_AVATAR);

        UserProfileResponse response = profileService.updateAvatar(avatarFile());

        assertThat(response.avatarUrl()).isEqualTo(NEW_AVATAR);
    }

    private CurrentAccount currentAccount(long accountId) {
        CurrentAccount currentAccount = mock(CurrentAccount.class);
        org.mockito.Mockito.when(currentAccount.requireId()).thenReturn(accountId);
        return currentAccount;
    }

    private MockMultipartFile avatarFile() {
        return new MockMultipartFile("file", "me.png", "image/png", bytes());
    }

    private byte[] bytes() {
        return new byte[] {1, 2, 3};
    }
}
