package com.prelude.assets;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.assets.persistence.AttachmentMapper;
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
 * Bounded in-module cleanup of stale PENDING_UPLOAD assets. Not a background
 * job runtime: failures are logged and retried on the next pass.
 */
@Slf4j
@Component
public class StalePendingAssetReconciler {

    private static final int BATCH_LIMIT = 100;

    private final AssetMapper assetMapper;
    private final AttachmentMapper attachmentMapper;
    private final ObjectStoragePort objectStoragePort;
    private final Duration stalePendingTtl;
    private final ScheduledExecutorService scheduler;

    public StalePendingAssetReconciler(
        AssetMapper assetMapper,
        AttachmentMapper attachmentMapper,
        ObjectStoragePort objectStoragePort,
        @Value("${prelude.storage.stale-pending-ttl:PT24H}") Duration stalePendingTtl,
        @Value("${prelude.storage.reconcile-interval-ms:300000}") long intervalMillis
    ) {
        this.assetMapper = assetMapper;
        this.attachmentMapper = attachmentMapper;
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

    void reconcileStalePendingAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minus(stalePendingTtl);
        List<Asset> staleAssets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
            .eq(Asset::getStatus, AssetStatus.PENDING_UPLOAD)
            .lt(Asset::getCreatedAt, cutoff)
            .last("LIMIT " + BATCH_LIMIT));
        for (Asset asset : staleAssets) {
            try {
                attachmentMapper.delete(new LambdaQueryWrapper<com.prelude.assets.persistence.StoredAttachment>()
                    .eq(com.prelude.assets.persistence.StoredAttachment::getAssetId, asset.getId()));
            } catch (RuntimeException exception) {
                log.warn("Failed to delete attachment metadata referencing stale asset {}", asset.getId());
            }
            try {
                objectStoragePort.delete(asset.getObjectKey());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending object {}; will retry next pass", asset.getObjectKey());
            }
            try {
                assetMapper.deleteById(asset.getId());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete stale pending asset {} metadata", asset.getId());
            }
        }
        if (!staleAssets.isEmpty()) {
            log.info("Reconciled {} stale pending assets", staleAssets.size());
        }
    }
}
