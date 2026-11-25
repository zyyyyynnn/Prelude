package com.prelude.assets;

import com.prelude.assets.application.port.AssetDeletionStore;
import com.prelude.assets.application.port.AssetDeletionStore.DeletionRequest;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
class AssetDeletionService {

    private final AssetDeletionStore deletionStore;
    private final AssetLookup assetLookup;
    private final ObjectStoragePort objectStoragePort;
    private final AssetDeletionProperties properties;

    @Transactional(rollbackFor = Exception.class)
    void register(AssetRow asset) {
        deletionStore.register(asset.accountId(), asset.id(), asset.objectKey());
    }

    void processNextBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<DeletionRequest> requests = deletionStore.claimDue(properties.batchSize(), now, properties.leaseDuration());
        for (DeletionRequest request : requests) {
            process(request);
        }
    }

    private void process(DeletionRequest request) {
        try {
            AssetRow asset = assetLookup.findById(request.assetId());
            if (asset != null
                && (!Objects.equals(asset.accountId(), request.accountId())
                    || !Objects.equals(asset.objectKey(), request.objectKey()))) {
                deletionStore.block(request, "ASSET_OWNERSHIP_OR_KEY_MISMATCH");
                log.error("Blocked asset deletion request {} because the asset no longer matches its registration",
                    request.id());
                return;
            }
            objectStoragePort.delete(request.objectKey());
            deletionStore.complete(request);
        } catch (RuntimeException exception) {
            String failure = exception.getClass().getSimpleName();
            log.warn("Asset deletion request {} failed; metadata remains for retry", request.id(), exception);
            try {
                deletionStore.retry(request, nextAttemptAt(request), failure);
            } catch (RuntimeException retryFailure) {
                log.error("Could not reschedule asset deletion request {}; its lease will recover it", request.id(),
                    retryFailure);
            }
        }
    }

    private LocalDateTime nextAttemptAt(DeletionRequest request) {
        long maximum = properties.retryMaxDelay().toMillis();
        long delay = properties.retryBaseDelay().toMillis();
        for (int attempt = 1; attempt < request.attemptCount() && delay < maximum; attempt++) {
            delay = delay > maximum / 2 ? maximum : delay * 2;
        }
        return LocalDateTime.now().plus(Duration.ofMillis(Math.min(delay, maximum)));
    }
}
