package com.prelude.assets.api;

/**
 * Public asset query contract for other modules. Authorization always
 * happens before any presigned URL is issued.
 */
public interface AssetQueryApi {

    AssetRef requireOwnedReadyAsset(Long accountId, Long assetId);

    String presignedGetUrl(Long accountId, Long assetId);
}
