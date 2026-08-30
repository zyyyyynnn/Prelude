package com.prelude.assets;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
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

/**
 * Bounded in-module cleanup of stale PENDING_UPLOAD assets. A stale PENDING
 * row has no business reference yet, so cleanup only needs the object and the
 * metadata row: object deletion failure keeps the row for the next idempotent
 * pass; only a confirmed object delete removes it. Not a background job runtime.
 */
@Slf4j
@Component
public class StalePendingAssetReconciler {

    private static final int BATCH_LIMIT = 100;

    private final AssetMapper assetMapper;
    private final ObjectStoragePort objectStoragePort;
    private final Duration stalePendingTtl;
    private final ScheduledExecutorService scheduler;

    public StalePendingAssetReconciler(
        AssetMapper assetMapper,
        ObjectStoragePort objectStoragePort,
        @Value("${prelude.storage.stale-pending-ttl:PT24H}") Duration stalePendingTtl,
        @Value("${prelude.storage.reconcile-interval-ms:300000}") long intervalMillis
    ) {
        this.assetMapper = assetMapper;
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
        List<Asset> staleAssets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
            .eq(Asset::getStatus, AssetStatus.PENDING_UPLOAD)
            .lt(Asset::getCreatedAt, cutoff)
            .last("LIMIT " + BATCH_LIMIT));
        for (Asset asset : staleAssets) {
            try {
                objectStoragePort.delete(asset.getObjectKey());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending object {}; the asset row remains for the next pass",
                    asset.getObjectKey());
                continue;
            }
            try {
                assetMapper.deleteById(asset.getId());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending asset {} metadata; retrying next pass", asset.getId());
            }
        }
        if (!staleAssets.isEmpty()) {
            log.info("Reconciled {} stale pending assets", staleAssets.size());
        }
    }
}
