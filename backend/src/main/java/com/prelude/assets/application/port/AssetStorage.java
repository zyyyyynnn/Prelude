package com.prelude.assets.application.port;

import com.prelude.assets.domain.AssetStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Storage access for the asset lifecycle. The reconciler and the asset services reach
 * rows through this port, so no use case holds a mapper or builds a query wrapper.
 */
public interface AssetStorage {

    /** Assets in one status created before the cutoff, bounded by {@code limit}. */
    List<StaleAssetRef> findStaleByStatus(AssetStatus status, LocalDateTime cutoff, int limit);

    void deleteById(Long assetId);
}
