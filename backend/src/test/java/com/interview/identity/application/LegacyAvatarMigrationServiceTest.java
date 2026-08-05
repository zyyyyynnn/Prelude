package com.interview.identity.application;

import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.LegacyAvatarMigrationPort;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.identity.application.port.ProcessedAvatar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyAvatarMigrationServiceTest {

    @Mock
    private LegacyAvatarMigrationPort repository;
    @Mock
    private LegacyAvatarSourcePort source;
    @Mock
    private AvatarContentProcessor processor;
    @Mock
    private AvatarStoragePort storage;

    private LegacyAvatarMigrationService service;

    @BeforeEach
    void setUp() {
        service = new LegacyAvatarMigrationService(
            repository,
            source,
            processor,
            storage,
            new TransactionTemplate(new TestTransactionManager())
        );
    }

    @Test
    void migratesSupportedLegacyMediaAndDeletesOldOnlyAfterCommit() {
        var candidate = candidate(7L, "/uploads/avatars/old.gif");
        var resource = resource("old.gif", "image/gif");
        when(source.read("old.gif")).thenReturn(LegacyAvatarSourcePort.ReadResult.supported(resource));
        when(processor.processLegacy(any(AvatarUpload.class))).thenReturn(processed());
        when(storage.store(anyString(), any(ProcessedAvatar.class)))
            .thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(repository.replaceLegacyAvatarUrl(anyLong(), anyString(), anyString())).thenReturn(1);

        assertThat(service.migrateOne(candidate)).isEqualTo(LegacyAvatarMigrationResult.MIGRATED);

        verify(processor).processLegacy(any(AvatarUpload.class));
        verify(repository).replaceLegacyAvatarUrl(eq(7L), eq("/uploads/avatars/old.gif"), anyString());
        verify(source).delete("old.gif");
        verify(storage, never()).delete(anyString());
    }

    @Test
    void keepsMissingInvalidAndUnsupportedWebpUrisUntouched() {
        var missing = candidate(1L, "/uploads/avatars/missing.png");
        var invalid = candidate(2L, "/uploads/avatars/invalid.png");
        var webp = candidate(3L, "/uploads/avatars/legacy.webp");
        when(source.read("missing.png")).thenReturn(LegacyAvatarSourcePort.ReadResult.missing());
        when(source.read("invalid.png")).thenReturn(LegacyAvatarSourcePort.ReadResult.invalid());
        var webpResource = resource("legacy.webp", "image/webp");
        when(source.read("legacy.webp"))
            .thenReturn(LegacyAvatarSourcePort.ReadResult.unsupportedWebp(webpResource));

        assertThat(service.migrateOne(missing)).isEqualTo(LegacyAvatarMigrationResult.MISSING);
        assertThat(service.migrateOne(invalid)).isEqualTo(LegacyAvatarMigrationResult.INVALID);
        assertThat(service.migrateOne(webp)).isEqualTo(LegacyAvatarMigrationResult.UNSUPPORTED_WEBP);
        verify(repository, never()).replaceLegacyAvatarUrl(anyLong(), anyString(), anyString());
        verify(storage, never()).store(anyString(), any(ProcessedAvatar.class));
    }

    @Test
    void rollsBackNewFileWhenDatabaseCommitFailsAndContinuesTheBatch() {
        var failed = candidate(1L, "/uploads/avatars/failed.png");
        var successful = candidate(2L, "/uploads/avatars/success.png");
        when(repository.findLegacyAvatarBatch(anyInt())).thenReturn(List.of(failed, successful));
        when(source.read("failed.png")).thenReturn(LegacyAvatarSourcePort.ReadResult.supported(resource("failed.png", "image/png")));
        when(source.read("success.png")).thenReturn(LegacyAvatarSourcePort.ReadResult.supported(resource("success.png", "image/png")));
        when(processor.processLegacy(any(AvatarUpload.class))).thenReturn(processed());
        when(storage.store(anyString(), any(ProcessedAvatar.class)))
            .thenAnswer(invocation -> stored(invocation.getArgument(0)));
        doThrow(new IllegalStateException("commit failed"))
            .when(repository).replaceLegacyAvatarUrl(eq(1L), eq("/uploads/avatars/failed.png"), anyString());
        when(repository.replaceLegacyAvatarUrl(eq(2L), eq("/uploads/avatars/success.png"), anyString())).thenReturn(1);

        service.migrateBatch(100);

        verify(storage).delete(anyString());
        verify(source).delete("success.png");
        verify(repository).replaceLegacyAvatarUrl(eq(2L), eq("/uploads/avatars/success.png"), anyString());
    }

    @Test
    void rejectsUnsafeLegacyKeyBeforeReadingTheFilesystem() {
        var candidate = candidate(9L, "/uploads/avatars/../secret.png");

        assertThat(service.migrateOne(candidate)).isEqualTo(LegacyAvatarMigrationResult.INVALID_KEY);

        verify(source, never()).read(anyString());
        verify(repository, never()).replaceLegacyAvatarUrl(anyLong(), anyString(), anyString());
    }

    private LegacyAvatarMigrationPort.LegacyAvatarCandidate candidate(Long userId, String uri) {
        var candidate = new LegacyAvatarMigrationPort.LegacyAvatarCandidate();
        candidate.setUserId(userId);
        candidate.setAvatarUrl(uri);
        return candidate;
    }

    private LegacyAvatarSourcePort.LegacyAvatarResource resource(String key, String type) {
        return new LegacyAvatarSourcePort.LegacyAvatarResource(
            key,
            type,
            3,
            new ByteArrayInputStream(new byte[] {1, 2, 3})
        );
    }

    private ProcessedAvatar processed() {
        return new ProcessedAvatar(new byte[] {4, 5}, "image/png", "png", 1, 1);
    }

    private AvatarStoragePort.StoredAvatar stored(String key) {
        return new AvatarStoragePort.StoredAvatar(key, "/media/avatars/" + key);
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
