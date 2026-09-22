package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.assets.application.port.AssetStorage;
import com.prelude.assets.application.port.StaleAssetRef;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.infrastructure.persistence.Asset;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link AssetStorage}; the only place that names the mapper. */
@Repository
@RequiredArgsConstructor
public class MybatisAssetStorage implements AssetStorage {

    private final AssetMapper assetMapper;

    @Override
    public List<StaleAssetRef> findStaleByStatus(AssetStatus status, LocalDateTime cutoff, int limit) {
        return assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getStatus, status)
                .lt(Asset::getCreatedAt, cutoff)
                .last("LIMIT " + limit))
            .stream()
            .map(asset -> new StaleAssetRef(asset.getId(), asset.getObjectKey()))
            .toList();
    }

    @Override
    public void deleteById(Long assetId) {
        assetMapper.deleteById(assetId);
    }
}
