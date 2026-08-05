package com.interview.identity.application;

import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.LegacyAvatarMigrationPort;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.identity.application.port.ProcessedAvatar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyAvatarMigrationService {

    private final LegacyAvatarMigrationPort repository;
    private final LegacyAvatarSourcePort legacyAvatarSource;
    private final AvatarContentProcessor avatarContentProcessor;
    private final AvatarStoragePort avatarStoragePort;
    private final TransactionTemplate transactionTemplate;

    public void migrateBatch(int batchSize) {
        List<LegacyAvatarMigrationPort.LegacyAvatarCandidate> candidates =
            repository.findLegacyAvatarBatch(Math.max(1, batchSize));
        for (LegacyAvatarMigrationPort.LegacyAvatarCandidate candidate : candidates) {
            LegacyAvatarMigrationResult result;
            try {
                result = migrateOne(candidate);
            } catch (RuntimeException exception) {
                result = LegacyAvatarMigrationResult.FAILED;
            }
            log.info("legacy avatar migration userId={} result={}", candidate.getUserId(), result);
        }
    }

    LegacyAvatarMigrationResult migrateOne(
        LegacyAvatarMigrationPort.LegacyAvatarCandidate candidate
    ) {
        String oldAvatarUrl = candidate.getAvatarUrl();
        String objectKey = AvatarObjectKeys.fromStoredUri(oldAvatarUrl).orElse(null);
        if (objectKey == null) {
            return LegacyAvatarMigrationResult.INVALID_KEY;
        }

        LegacyAvatarSourcePort.ReadResult readResult = legacyAvatarSource.read(objectKey);
        if (readResult.status() == LegacyAvatarSourcePort.Status.MISSING) {
            return LegacyAvatarMigrationResult.MISSING;
        }
        if (readResult.status() == LegacyAvatarSourcePort.Status.INVALID) {
            return LegacyAvatarMigrationResult.INVALID;
        }
        if (readResult.status() == LegacyAvatarSourcePort.Status.UNSUPPORTED_WEBP) {
            closeQuietly(readResult.resource());
            return LegacyAvatarMigrationResult.UNSUPPORTED_WEBP;
        }

        LegacyAvatarSourcePort.LegacyAvatarResource resource = readResult.resource();
        ProcessedAvatar processed;
        try {
            try (resource) {
                try {
                    processed = avatarContentProcessor.processLegacy(new AvatarUpload(
                        objectKey,
                        resource.contentType(),
                        resource.contentLength(),
                        resource.content()
                    ));
                } catch (RuntimeException exception) {
                    return LegacyAvatarMigrationResult.INVALID;
                }
            }
        } catch (java.io.IOException exception) {
            return LegacyAvatarMigrationResult.INVALID;
        }

        String newObjectKey = AvatarObjectKeys.forUser(candidate.getUserId(), processed.extension());
        AvatarStoragePort.StoredAvatar stored = avatarStoragePort.store(newObjectKey, processed);
        try {
            LegacyAvatarMigrationResult result = transactionTemplate.execute(status -> {
                int updated = repository.replaceLegacyAvatarUrl(
                    candidate.getUserId(),
                    oldAvatarUrl,
                    stored.publicUri()
                );
                if (updated != 1) {
                    return LegacyAvatarMigrationResult.STALE;
                }
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("历史头像迁移必须运行在事务中");
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(objectKey, candidate.getUserId());
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            deleteNewQuietly(stored.objectKey(), candidate.getUserId());
                        }
                    }
                });
                return LegacyAvatarMigrationResult.MIGRATED;
            });
            if (result == LegacyAvatarMigrationResult.STALE) {
                deleteNewQuietly(stored.objectKey(), candidate.getUserId());
            }
            return result == null ? LegacyAvatarMigrationResult.FAILED : result;
        } catch (RuntimeException exception) {
            deleteNewQuietly(stored.objectKey(), candidate.getUserId());
            throw exception;
        }
    }

    private void deleteQuietly(String objectKey, Long userId) {
        try {
            legacyAvatarSource.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("legacy avatar migration userId={} result=CLEANUP_FAILED", userId);
        }
    }

    private void deleteNewQuietly(String objectKey, Long userId) {
        try {
            avatarStoragePort.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("legacy avatar migration userId={} result=CLEANUP_FAILED", userId);
        }
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception exception) {
            // The source has already completed the migration decision; close failure is non-fatal.
        }
    }
}
