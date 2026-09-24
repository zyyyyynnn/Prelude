package com.prelude.assets.application.port;

import com.prelude.assets.application.port.AssetLookup.AssetRow;

/**
 * AssetEntity row writes. Reads go through {@link AssetLookup}; this port owns the
 * lifecycle transitions, so no use case names the asset mapper or builds a wrapper.
 */
public interface AssetLifecycleStore {

    /** Persists a PENDING_UPLOAD row and returns it with its generated identifier. */
    AssetRow createPending(Long accountId, String kind, String mediaType, long byteSize);

    /**
     * Moves a PENDING_UPLOAD row to READY. Returns the rows written, so a row that
     * already left PENDING (or never was one) is reported rather than assumed.
     */
    int markReady(Long assetId);
}
