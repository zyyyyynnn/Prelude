package com.prelude.assets;

import com.prelude.assets.api.AssetQueryApi;
import com.prelude.assets.api.AssetRef;
import com.prelude.assets.persistence.Asset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetQueryFacade implements AssetQueryApi {

    private final AssetService assetService;

    @Override
    public AssetRef requireOwnedReadyAsset(Long accountId, Long assetId) {
        Asset asset = assetService.requireOwnedReady(accountId, assetId);
        return new AssetRef(asset.getId());
    }

    @Override
    public String presignedGetUrl(Long accountId, Long assetId) {
        Asset asset = assetService.requireOwnedReady(accountId, assetId);
        return assetService.presignGet(asset);
    }
}
