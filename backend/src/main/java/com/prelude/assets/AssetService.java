package com.prelude.assets;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.BusinessException;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.infrastructure.S3StorageConfiguration;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Asset lifecycle: create PENDING_UPLOAD, upload the binary, mark READY.
 * Every asset owns a fresh, unpredictable object key; existing objects are
 * never overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetMapper assetMapper;
    private final ObjectStoragePort objectStoragePort;
    private final S3StorageConfiguration.S3StorageProperties s3Properties;

    public Asset createPending(Long accountId, String kind, String mediaType, long byteSize) {
        Asset asset = new Asset();
        asset.setAccountId(accountId);
        asset.setKind(kind);
        asset.setObjectKey(UUID.randomUUID().toString());
        asset.setMediaType(mediaType);
        asset.setByteSize(byteSize);
        asset.setStatus(AssetStatus.PENDING_UPLOAD);
        assetMapper.insert(asset);
        return asset;
    }

    public boolean markReady(Long assetId) {
        return assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
            .set(Asset::getStatus, AssetStatus.READY)
            .eq(Asset::getId, assetId)
            .eq(Asset::getStatus, AssetStatus.PENDING_UPLOAD)) == 1;
    }

    public Asset requireOwnedReady(Long accountId, Long assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getAccountId() == null || !asset.getAccountId().equals(accountId)
            || asset.getStatus() != AssetStatus.READY) {
            throw BusinessException.notFound("资产不存在");
        }
        return asset;
    }

    public byte[] readContent(Asset asset) {
        return objectStoragePort.get(asset.getObjectKey());
    }

    public String presignGet(Asset asset) {
        return objectStoragePort.presignGet(asset.getObjectKey(), presignTtl());
    }

    public void delete(Asset asset) {
        try {
            objectStoragePort.delete(asset.getObjectKey());
        } catch (RuntimeException exception) {
            log.warn("Failed to delete object {} for asset {}", asset.getObjectKey(), asset.getId(), exception);
        }
        assetMapper.deleteById(asset.getId());
    }

    private Duration presignTtl() {
        return s3Properties.presignTtl() == null ? Duration.ofMinutes(10) : s3Properties.presignTtl();
    }
}
