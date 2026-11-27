package com.prelude.assets;

import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.StaleAssetRef;
import com.prelude.assets.domain.AssetStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StalePendingAssetReconciler {

    private static final int BATCH_LIMIT = 100;

    private final AssetRepository assetRepository;
    private final ObjectStoragePort objectStoragePort;
    private final Duration stalePendingTtl;
    private final ScheduledExecutorService scheduler;

    public StalePendingAssetReconciler(
        AssetRepository assetRepository,
        ObjectStoragePort objectStoragePort,
        @Value("${prelude.storage.stale-pending-ttl:PT24H}") Duration stalePendingTtl,
        @Value("${prelude.storage.reconcile-interval-ms:300000}") long intervalMillis
    ) {
        this.assetRepository = assetRepository;
        this.objectStoragePort = objectStoragePort;
        this.stalePendingTtl = stalePendingTtl;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "asset-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleWithFixedDelay(
            this::reconcileStalePendingAssets,
            Math.max(intervalMillis, 60_000L),
            Math.max(intervalMillis, 60_000L),
            TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    void reconcileStalePendingAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minus(stalePendingTtl);
        List<StaleAssetRef> staleAssets =
            assetRepository.findStaleByStatus(AssetStatus.PENDING_UPLOAD, cutoff, BATCH_LIMIT);
        for (StaleAssetRef asset : staleAssets) {
            try {
                objectStoragePort.delete(asset.objectKey());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending object {}; the asset row remains for the next pass",
                    asset.objectKey());
                continue;
            }
            try {
                assetRepository.deleteById(asset.assetId());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending asset {} metadata; retrying next pass", asset.assetId());
            }
        }
        if (!staleAssets.isEmpty()) {
            log.info("Reconciled {} stale pending assets", staleAssets.size());
        }
    }
}
