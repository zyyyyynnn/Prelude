package com.prelude.test;

import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.StoredAttachment;

public final class AssetFixtures {

    private AssetFixtures() {
    }

    public static StoredAttachment stored(Long id, Long accountId, Long assetId, String fileName, String scopeType, Long scopeId) {
        StoredAttachment stored = new StoredAttachment();
        stored.setId(id);
        stored.setAccountId(accountId);
        stored.setAssetId(assetId);
        stored.setFileName(fileName);
        stored.setScopeType(scopeType);
        stored.setScopeId(scopeId);
        return stored;
    }

    public static StoredAttachment stored(Long id, Long assetId, String fileName) {
        return stored(id, 7L, assetId, fileName, "session", 9L);
    }

    public static Asset asset(Long id, String mediaType, long byteSize) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setMediaType(mediaType);
        asset.setByteSize(byteSize);
        return asset;
    }

    public static AssetStatus statusPendingUpload() {
        return AssetStatus.PENDING_UPLOAD;
    }

    public static AssetStatus statusReady() {
        return AssetStatus.READY;
    }

    public static boolean isPendingUpload(Object status) {
        return AssetStatus.PENDING_UPLOAD.equals(status);
    }
}