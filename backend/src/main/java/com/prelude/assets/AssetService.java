package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.AssetRepository.AssetRow;
import com.prelude.assets.domain.AssetStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final ObjectStoragePort objectStoragePort;
    private final S3StorageProperties s3Properties;

    public AssetRow createPending(Long accountId, String kind, String mediaType, long byteSize) {
        return assetRepository.createPending(accountId, kind, mediaType, byteSize);
    }

    public boolean markReady(Long assetId) {
        return assetRepository.markReady(assetId) == 1;
    }

    public AssetRow requireOwnedReady(Long accountId, Long assetId) {
        AssetRow asset = assetRepository.findById(assetId);
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

    private Duration presignTtl() {
        return s3Properties.presignTtl() == null ? Duration.ofMinutes(10) : s3Properties.presignTtl();
    }
}
