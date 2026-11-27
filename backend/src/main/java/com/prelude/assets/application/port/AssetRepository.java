package com.prelude.assets.application.port;

import com.prelude.assets.domain.AssetStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AssetRepository {

    AssetRow findById(Long assetId);

    List<AssetRow> findByIds(List<Long> assetIds);

    AssetRow createPending(Long accountId, String kind, String mediaType, long byteSize);

    int markReady(Long assetId);

    List<StaleAssetRef> findStaleByStatus(AssetStatus status, LocalDateTime cutoff, int limit);

    void deleteById(Long assetId);

    int deleteByIdAndAccountId(Long assetId, Long accountId);

    record AssetRow(
        Long id,
        Long accountId,
        String kind,
        String objectKey,
        String mediaType,
        Long byteSize,
        String status
    ) {
    }
}
