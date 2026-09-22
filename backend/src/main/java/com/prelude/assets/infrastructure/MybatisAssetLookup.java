package com.prelude.assets.infrastructure;

import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.infrastructure.persistence.Asset;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link AssetLookup}; the only place that names the asset mapper. */
@Repository
@RequiredArgsConstructor
public class MybatisAssetLookup implements AssetLookup {

    private final AssetMapper assetMapper;

    @Override
    public AssetRow findById(Long assetId) {
        Asset asset = assetMapper.selectById(assetId);
        return asset == null ? null : toRow(asset);
    }

    @Override
    public List<AssetRow> findByIds(List<Long> assetIds) {
        return assetMapper.selectBatchIds(assetIds).stream()
            .map(this::toRow)
            .toList();
    }

    @Override
    public void deleteById(Long assetId) {
        assetMapper.deleteById(assetId);
    }

    private AssetRow toRow(Asset asset) {
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
