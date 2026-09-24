package com.prelude.test;

import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.StoredAttachmentEntity;

public final class AssetFixtures {

    private AssetFixtures() {
    }

    public static StoredAttachmentEntity stored(Long id, Long accountId, Long assetId, String fileName, String scopeType, Long scopeId) {
        StoredAttachmentEntity stored = new StoredAttachmentEntity();
        stored.setId(id);
        stored.setAccountId(accountId);
        stored.setAssetId(assetId);
        stored.setFileName(fileName);
        stored.setScopeType(scopeType);
        stored.setScopeId(scopeId);
        return stored;
    }

    public static StoredAttachmentEntity stored(Long id, Long assetId, String fileName) {
        return stored(id, 7L, assetId, fileName, "session", 9L);
    }

    public static AssetEntity asset(Long id, String mediaType, long byteSize) {
        AssetEntity asset = new AssetEntity();
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

    public static com.prelude.assets.application.port.AttachmentStorage.AttachmentRow attachmentRow(
        Long id, Long assetId, String fileName) {
        return new com.prelude.assets.application.port.AttachmentStorage.AttachmentRow(
            id, 7L, assetId, fileName, "text", null, null);
    }

    public static com.prelude.assets.application.port.AssetLookup.AssetRow assetRow(
        Long id, String mediaType, Long byteSize) {
        return new com.prelude.assets.application.port.AssetLookup.AssetRow(
            id, 7L, "attachment", "key-" + id, mediaType, byteSize, "READY");
    }
}
