package com.prelude.assets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "prelude.storage.deletion", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
class AssetDeletionScheduler {

    private final AssetDeletionService deletionService;

    @Scheduled(fixedDelayString = "${prelude.storage.deletion.interval-ms:300000}")
    void deleteRequestedAssets() {
        try {
            deletionService.processNextBatch();
        } catch (RuntimeException exception) {
            log.warn("Asset deletion pass failed; the next pass will reclaim pending work", exception);
        }
    }
}
