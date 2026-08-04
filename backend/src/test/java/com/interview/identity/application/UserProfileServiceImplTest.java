package com.interview.identity.application;

import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.shared.web.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    private TransactionTemplate transactionTemplate;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(new LockingTransactionManager());
        service = new UserProfileServiceImpl(
            userMapper,
            passwordEncoder,
            contentProcessor,
            storage,
            transactionTemplate
        );
        UserContext.setCurrentUserId(42L);
        when(contentProcessor.process(any())).thenReturn(
            new ProcessedAvatar(new byte[] {1, 2, 3}, "image/png", "png", 1, 1)
        );
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void commitsProfileUpdateAndDeletesOldFileAfterCommit() {
        User current = user("/uploads/avatars/old.jpg");
        when(userMapper.selectById(42L)).thenReturn(current);
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(current);
        when(storage.store(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return new AvatarStoragePort.StoredAvatar(key, "/media/avatars/" + key);
        });

        var response = service.updateAvatar(upload());

        assertThat(response.avatarUrl()).startsWith("/media/avatars/42_").endsWith(".png");
        verify(storage).delete("old.jpg");
        verify(userMapper).selectByIdForUpdate(42L);
    }

    @Test
    void rollbackRemovesNewFileAndDoesNotDeleteOldFile() {
        User current = user("/media/avatars/old.png");
        when(userMapper.selectById(42L)).thenReturn(current);
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(current);
        when(storage.store(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return new AvatarStoragePort.StoredAvatar(key, "/media/avatars/" + key);
        });
        doThrow(new IllegalStateException("database failure")).when(userMapper).updateById(any(User.class));

        assertThatThrownBy(() -> service.updateAvatar(upload()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database failure");

        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(storage, org.mockito.Mockito.atLeastOnce()).delete(deleted.capture());
        assertThat(deleted.getAllValues()).allMatch(value -> value.startsWith("42_") && value.endsWith(".png"));
        verify(storage, never()).delete("old.png");
    }

    @Test
    void concurrentUploadsSerializeLockedRowAndLeaveDatabasePointingAtOneStoredFile() throws Exception {
        User state = user("/media/avatars/old.png");
        when(userMapper.selectById(anyLong())).thenAnswer(invocation -> user(state.getAvatarUrl()));
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(state);

        CountDownLatch bothStored = new CountDownLatch(2);
        List<String> storedKeys = new ArrayList<>();
        when(storage.store(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            synchronized (storedKeys) {
                storedKeys.add(key);
            }
            bothStored.countDown();
            assertThat(bothStored.await(5, TimeUnit.SECONDS)).isTrue();
            return new AvatarStoragePort.StoredAvatar(key, "/media/avatars/" + key);
        });
        doAnswer(invocation -> {
            User update = invocation.getArgument(0);
            state.setAvatarUrl(update.getAvatarUrl());
            return 1;
        }).when(userMapper).updateById(any(User.class));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runUploadInThread());
            var second = executor.submit(() -> runUploadInThread());
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(state.getAvatarUrl()).isIn(
            storedKeys.stream().map(key -> "/media/avatars/" + key).toArray(String[]::new)
        );
        verify(storage).delete("old.png");
        for (String key : storedKeys) {
            if (!state.getAvatarUrl().equals("/media/avatars/" + key)) {
                verify(storage).delete(key);
            }
        }
    }

    private void runUploadInThread() {
        UserContext.setCurrentUserId(42L);
        try {
            service.updateAvatar(upload());
        } finally {
            UserContext.remove();
        }
    }

    private User user(String avatarUrl) {
        User user = new User();
        user.setId(42L);
        user.setUsername("demo");
        user.setEmail("demo@example.com");
        user.setThemePreference("system");
        user.setAvatarUrl(avatarUrl);
        return user;
    }

    private AvatarUpload upload() {
        return new AvatarUpload("../../avatar.png", "image/png", 3, new ByteArrayInputStream(new byte[] {1, 2, 3}));
    }

    private static final class LockingTransactionManager extends AbstractPlatformTransactionManager {

        private final ReentrantLock lock = new ReentrantLock();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            lock.lock();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            lock.unlock();
        }
    }
}
