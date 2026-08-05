package com.interview.identity.application;

import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;
    @Mock
    private AvatarContentProcessor contentProcessor;
    @Mock
    private AvatarStoragePort storage;
    @Mock
    private LegacyAvatarSourcePort legacySource;

    private TransactionTemplate transactionTemplate;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(new TestTransactionManager());
        service = new UserProfileServiceImpl(
            userMapper,
            passwordEncoder,
            contentProcessor,
            storage,
            legacySource,
            transactionTemplate
        );
        UserContext.setCurrentUserId(42L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void commitsAvatarByRevisionAndDeletesOldFileAfterCommit() {
        stubProcessor();
        User current = user("/uploads/avatars/old.jpg", 0L);
        stubRevisionClaim(current);
        when(storage.store(any(), any())).thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(userMapper.updateAvatarIfRevision(anyLong(), any(), anyLong())).thenAnswer(invocation -> {
            current.setAvatarUrl(invocation.getArgument(1));
            return 1;
        });

        var response = service.updateAvatar(upload());

        assertThat(response.avatarUrl()).startsWith("/media/avatars/42_").endsWith(".png");
        verify(userMapper).claimAvatarRevision(42L);
        verify(userMapper).updateAvatarIfRevision(42L, response.avatarUrl(), 1L);
        verify(legacySource).delete("old.jpg");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void rollbackRemovesNewFileAndDoesNotDeleteOldFile() {
        stubProcessor();
        User current = user("/media/avatars/old.png", 0L);
        stubRevisionClaim(current);
        when(storage.store(any(), any())).thenAnswer(invocation -> stored(invocation.getArgument(0)));
        doThrow(new IllegalStateException("database failure"))
            .when(userMapper).updateAvatarIfRevision(anyLong(), any(), anyLong());

        assertThatThrownBy(() -> service.updateAvatar(upload()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database failure");

        verify(storage).delete(any());
        verify(storage, never()).delete("old.png");
    }

    @Test
    void staleAvatarRevisionReturnsConflictAndDeletesOnlyTheNewFile() {
        stubProcessor();
        User current = user("/media/avatars/current.png", 0L);
        stubRevisionClaim(current);
        when(storage.store(any(), any())).thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(userMapper.updateAvatarIfRevision(anyLong(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.updateAvatar(upload()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getCode())
            .isEqualTo(409);

        verify(storage).delete(any());
        verify(storage, never()).delete("current.png");
    }

    @Test
    void profileMutationUsesFieldLevelWritesAndNeverPersistsAFullUserEntity() {
        User current = user("/media/avatars/current.png", 0L);
        current.setUsername("before");
        current.setEmail("before@example.com");
        current.setPassword("old-hash");
        current.setThemePreference("system");
        when(userMapper.selectById(42L)).thenReturn(current);
        when(userMapper.updateUsername(42L, "after")).thenAnswer(invocation -> {
            current.setUsername("after");
            return 1;
        });
        when(userMapper.updateEmail(42L, "after@example.com")).thenAnswer(invocation -> {
            current.setEmail("after@example.com");
            return 1;
        });
        when(userMapper.updateThemePreference(42L, "dark")).thenAnswer(invocation -> {
            current.setThemePreference("dark");
            return 1;
        });
        when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("new-hash");

        UserProfileRequest request = new UserProfileRequest();
        request.setUsername("after");
        request.setEmail("after@example.com");
        request.setThemePreference("dark");
        request.setOldPassword("old");
        request.setNewPassword("new");

        var response = service.updateCurrentUserProfile(request);

        assertThat(response.username()).isEqualTo("after");
        assertThat(response.email()).isEqualTo("after@example.com");
        assertThat(response.themePreference()).isEqualTo("dark");
        verify(userMapper).updateUsername(42L, "after");
        verify(userMapper).updateEmail(42L, "after@example.com");
        verify(userMapper).updateThemePreference(42L, "dark");
        verify(userMapper).updatePassword(42L, "new-hash");
        verify(userMapper, never()).updateById(any(User.class));
    }

    private void stubRevisionClaim(User current) {
        when(userMapper.selectById(42L)).thenReturn(current);
        when(userMapper.selectAvatarUrlForUpdate(42L)).thenAnswer(invocation -> current.getAvatarUrl());
        doAnswer(invocation -> {
            current.setAvatarRevision(current.getAvatarRevision() + 1);
            return 1;
        }).when(userMapper).claimAvatarRevision(42L);
    }

    private void stubProcessor() {
        when(contentProcessor.process(any())).thenReturn(
            new ProcessedAvatar(new byte[] {1, 2, 3}, "image/png", "png", 1, 1)
        );
    }

    private AvatarStoragePort.StoredAvatar stored(String key) {
        return new AvatarStoragePort.StoredAvatar(key, "/media/avatars/" + key);
    }

    private User user(String avatarUrl, long revision) {
        User user = new User();
        user.setId(42L);
        user.setUsername("demo");
        user.setEmail("demo@example.com");
        user.setThemePreference("system");
        user.setAvatarUrl(avatarUrl);
        user.setAvatarRevision(revision);
        user.setPassword("old-hash");
        return user;
    }

    private AvatarUpload upload() {
        return new AvatarUpload(
            "../../avatar.png",
            "image/png",
            3,
            new ByteArrayInputStream(new byte[] {1, 2, 3})
        );
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
        }
    }
}
