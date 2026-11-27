package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.AssetRepository.AssetRow;
import com.prelude.assets.application.port.StaleAssetRef;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisAssetRepository implements AssetRepository {

    private final AssetMapper assetMapper;

    @Override
    public AssetRow findById(Long assetId) {
        AssetEntity asset = assetMapper.selectById(assetId);
        return asset == null ? null : toRow(asset);
    }

    @Override
    public List<AssetRow> findByIds(List<Long> assetIds) {
        return assetMapper.selectBatchIds(assetIds).stream()
            .map(this::toRow)
            .toList();
    }

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
        return toRow(asset);
    }

    @Override
    public int markReady(Long assetId) {
        return assetMapper.update(null, new LambdaUpdateWrapper<AssetEntity>()
            .set(AssetEntity::getStatus, AssetStatus.READY)
            .eq(AssetEntity::getId, assetId)
            .eq(AssetEntity::getStatus, AssetStatus.PENDING_UPLOAD));
    }

    @Override
    public List<StaleAssetRef> findStaleByStatus(AssetStatus status, LocalDateTime cutoff, int limit) {
        return assetMapper.selectList(new LambdaQueryWrapper<AssetEntity>()
                .eq(AssetEntity::getStatus, status)
                .lt(AssetEntity::getCreatedAt, cutoff)
                .last("LIMIT " + limit))
            .stream()
            .map(asset -> new StaleAssetRef(asset.getId(), asset.getObjectKey()))
            .toList();
    }

    @Override
    public void deleteById(Long assetId) {
        assetMapper.deleteById(assetId);
    }

    @Override
    public int deleteByIdAndAccountId(Long assetId, Long accountId) {
        return assetMapper.delete(new LambdaQueryWrapper<AssetEntity>()
            .eq(AssetEntity::getId, assetId)
            .eq(AssetEntity::getAccountId, accountId));
    }

    private AssetRow toRow(AssetEntity asset) {
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
}
