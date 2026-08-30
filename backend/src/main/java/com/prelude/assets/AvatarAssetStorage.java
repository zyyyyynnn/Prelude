package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarAssetStorage implements AvatarStoragePort {

    private static final String KIND_AVATAR = "avatar";
    private static final String AVATAR_URL_PREFIX = "/api/assets/";
    private static final String AVATAR_URL_SUFFIX = "/content";

    private final AssetService assetService;
    private final AssetMapper assetMapper;
    private final ObjectStoragePort objectStoragePort;

    @Override
    public String store(Long accountId, String mediaType, byte[] bytes) {
        Asset asset = assetService.createPending(accountId, KIND_AVATAR, mediaType, bytes.length);
        try {
            objectStoragePort.put(asset.getObjectKey(), mediaType, bytes);
        } catch (RuntimeException exception) {
            // The PENDING row stays as the recovery anchor for the reconciler.
            throw BusinessException.badRequest("头像上传失败");
        }
        if (!assetService.markReady(asset.getId())) {
            throw BusinessException.badRequest("头像上传失败");
        }
        return AVATAR_URL_PREFIX + asset.getId() + AVATAR_URL_SUFFIX;
    }

    @Override
    public void discard(Long accountId, String avatarUrl) {
        Asset asset = resolveOwnedAsset(accountId, avatarUrl);
        if (asset != null) {
            assetService.delete(asset);
        }
    }

    private Asset resolveOwnedAsset(Long accountId, String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX) || !avatarUrl.endsWith(AVATAR_URL_SUFFIX)) {
            return null;
        }
        String idSegment = avatarUrl.substring(
            AVATAR_URL_PREFIX.length(), avatarUrl.length() - AVATAR_URL_SUFFIX.length());
        try {
            Long assetId = Long.valueOf(idSegment);
            Asset asset = assetMapper.selectById(assetId);
            return asset != null && accountId.equals(asset.getAccountId()) && KIND_AVATAR.equals(asset.getKind())
                ? asset
                : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
