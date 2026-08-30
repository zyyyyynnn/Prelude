package com.prelude.assets.domain;

/**
 * Only two lifecycle states exist: an asset is either awaiting its binary
 * upload or it is ready. Stale pending assets are reclaimed by the
 * in-module bounded reconciler.
 */
public enum AssetStatus {
    PENDING_UPLOAD,
    READY
}
