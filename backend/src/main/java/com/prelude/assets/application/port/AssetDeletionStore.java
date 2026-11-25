package com.prelude.assets.application.port;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public interface AssetDeletionStore {

    void register(Long accountId, Long assetId, String objectKey);

    List<DeletionRequest> claimDue(int limit, LocalDateTime now, Duration leaseDuration);

    void complete(DeletionRequest request);

    void retry(DeletionRequest request, LocalDateTime nextAttemptAt, String failure);

    void block(DeletionRequest request, String failure);

    record DeletionRequest(
        Long id,
        Long accountId,
        Long assetId,
        String objectKey,
        int attemptCount
    ) {
    }
}
