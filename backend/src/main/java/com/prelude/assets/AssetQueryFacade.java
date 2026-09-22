package com.prelude.assets;

import com.prelude.assets.api.AssetQueryApi;
import com.prelude.assets.api.AssetRef;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetQueryFacade implements AssetQueryApi {

    private final AssetService assetService;

    @Override
    public AssetRef requireOwnedReadyAsset(Long accountId, Long assetId) {
        return new AssetRef(assetService.requireOwnedReady(accountId, assetId).id());
    }

    @Override
    public String presignedGetUrl(Long accountId, Long assetId) {
        AssetRow asset = assetService.requireOwnedReady(accountId, assetId);
        return assetService.presignGet(asset);
    }
}
