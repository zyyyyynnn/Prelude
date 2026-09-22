package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.application.port.AssetLifecycleStore;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.domain.AssetStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Asset lifecycle: create PENDING_UPLOAD, upload the binary, mark READY.
 * Every asset owns a fresh, unpredictable object key; existing objects are
 * never overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetLifecycleStore lifecycleStore;
    private final AssetLookup assetLookup;
    private final ObjectStoragePort objectStoragePort;
    private final S3StorageProperties s3Properties;

    public AssetRow createPending(Long accountId, String kind, String mediaType, long byteSize) {
        return lifecycleStore.createPending(accountId, kind, mediaType, byteSize);
    }

    public boolean markReady(Long assetId) {
        return lifecycleStore.markReady(assetId) == 1;
    }

    public AssetRow requireOwnedReady(Long accountId, Long assetId) {
        AssetRow asset = assetLookup.findById(assetId);
        if (asset == null || asset.accountId() == null || !asset.accountId().equals(accountId)) {
            throw BusinessException.notFound("素材不存在");
        }
        if (!AssetStatus.READY.name().equals(asset.status())) {
            throw BusinessException.notFound("素材不存在");
        }
        return asset;
    }

    public byte[] readContent(AssetRow asset) {
        return objectStoragePort.get(asset.objectKey());
    }

    public String presignGet(AssetRow asset) {
        return objectStoragePort.presignGet(asset.objectKey(), presignTtl());
    }

    /**
     * Deletes the remote object first; only a confirmed delete (S3 delete is
     * idempotent, a missing object counts as deleted) may remove the DB row.
     * On storage failure the metadata stays as the recovery anchor.
     */
    public void delete(AssetRow asset) {
        objectStoragePort.delete(asset.objectKey());
        assetLookup.deleteById(asset.id());
    }

    private Duration presignTtl() {
        return s3Properties.presignTtl() == null ? Duration.ofMinutes(10) : s3Properties.presignTtl();
    }
}
