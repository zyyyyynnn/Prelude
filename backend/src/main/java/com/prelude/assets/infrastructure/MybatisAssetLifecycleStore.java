package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.assets.application.port.AssetLifecycleStore;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link AssetLifecycleStore}. */
@Repository
@RequiredArgsConstructor
public class MybatisAssetLifecycleStore implements AssetLifecycleStore {

    private final AssetMapper assetMapper;

    @Override
    public AssetRow createPending(Long accountId, String kind, String mediaType, long byteSize) {
        AssetEntity asset = new AssetEntity();
        asset.setAccountId(accountId);
        asset.setKind(kind);
        asset.setObjectKey(UUID.randomUUID().toString());
        asset.setMediaType(mediaType);
        asset.setByteSize(byteSize);
        asset.setStatus(AssetStatus.PENDING_UPLOAD);
        assetMapper.insert(asset);
        return new AssetRow(
            asset.getId(),
            asset.getAccountId(),
            asset.getKind(),
            asset.getObjectKey(),
            asset.getMediaType(),
            asset.getByteSize(),
            asset.getStatus() == null ? null : asset.getStatus().name()
        );
    }

    @Override
    public int markReady(Long assetId) {
        return assetMapper.update(null, new LambdaUpdateWrapper<AssetEntity>()
            .set(AssetEntity::getStatus, AssetStatus.READY)
            .eq(AssetEntity::getId, assetId)
            .eq(AssetEntity::getStatus, AssetStatus.PENDING_UPLOAD));
    }
}
