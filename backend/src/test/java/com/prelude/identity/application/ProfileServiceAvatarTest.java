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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceAvatarTest {

    private static final String OLD_AVATAR = "/api/assets/5/content";
    private static final String CANDIDATE_AVATAR = "/api/assets/9/content";

    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final AvatarStoragePort avatarStoragePort = mock(AvatarStoragePort.class);
    private final AvatarPublication avatarPublication = mock(AvatarPublication.class);
    private final ProfileService profileService = new ProfileService(
        accountMapper,
        mock(PasswordEncoder.class),
        currentAccount(7L),
        avatarStoragePort,
        avatarPublication
    );

    private final Account account = new Account();

    @BeforeEach
    void prepareAccount() {
        account.setId(7L);
        account.setUsername("candidate");
        account.setRevision(5L);
        account.setAvatarUrl(OLD_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(avatarStoragePort.stage(7L, "image/png", bytes())).thenReturn(CANDIDATE_AVATAR);
    }

    @Test
    void conflictRollsBackThePublicationDiscardsOnlyTheCandidateAndKeepsTheOldReference() {
        doThrow(BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试"))
            .when(avatarPublication).publish(eq(CANDIDATE_AVATAR), eq(7L), any(), any(), any(), any(), eq(5L));

        assertThatThrownBy(() -> profileService.updateAvatar(avatarFile()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "revision_conflict");

        verify(avatarStoragePort).discard(7L, CANDIDATE_AVATAR);
        verify(avatarStoragePort, never()).discard(7L, OLD_AVATAR);
        assertThat(account.getAvatarUrl()).isEqualTo(OLD_AVATAR);
    }

    @Test
    void successCommitsTheReferenceThenCleansUpTheObsoleteAvatar() {
        Account reloaded = new Account();
        reloaded.setId(7L);
        reloaded.setUsername("candidate");
        reloaded.setRevision(6L);
        reloaded.setAvatarUrl(CANDIDATE_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account, reloaded);

        UserProfileResponse response = profileService.updateAvatar(avatarFile());

        assertThat(response.avatarUrl()).isEqualTo(CANDIDATE_AVATAR);
        verify(avatarPublication).publish(eq(CANDIDATE_AVATAR), eq(7L), eq("candidate"), any(), any(), any(), eq(5L));
        verify(avatarStoragePort).discard(7L, OLD_AVATAR);
        verify(avatarStoragePort, never()).discard(7L, CANDIDATE_AVATAR);
    }

    @Test
    void obsoleteAvatarCleanupFailureDoesNotRollBackTheCommittedReference() {
        Account reloaded = new Account();
        reloaded.setId(7L);
        reloaded.setUsername("candidate");
        reloaded.setRevision(6L);
        reloaded.setAvatarUrl(CANDIDATE_AVATAR);
        when(accountMapper.selectById(7L)).thenReturn(account, reloaded);
        doThrow(new IllegalStateException("storage down"))
            .when(avatarStoragePort).discard(7L, OLD_AVATAR);

        UserProfileResponse response = profileService.updateAvatar(avatarFile());

        assertThat(response.avatarUrl()).isEqualTo(CANDIDATE_AVATAR);
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
