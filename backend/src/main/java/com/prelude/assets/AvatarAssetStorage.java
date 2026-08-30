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

    private final AssetService assetService;
    private final AssetMapper assetMapper;
    private final ObjectStoragePort objectStoragePort;

    @Override
    public String store(Long accountId, String previousAvatarUrl, String mediaType, byte[] bytes) {
        Asset asset = assetService.createPending(accountId, KIND_AVATAR, mediaType, bytes.length);
        try {
            objectStoragePort.put(asset.getObjectKey(), mediaType, bytes);
        } catch (RuntimeException exception) {
            assetService.delete(asset);
            throw BusinessException.badRequest("头像上传失败");
        }
        if (!assetService.markReady(asset.getId())) {
            throw BusinessException.badRequest("头像上传失败");
        }
        removePrevious(accountId, previousAvatarUrl);
        return AVATAR_URL_PREFIX + asset.getId();
    }

    @Override
    public void discard(String avatarUrl) {
        Asset asset = resolveOwnedAsset(avatarUrl);
        if (asset != null) {
            assetService.delete(asset);
        }
    }

    private void removePrevious(Long accountId, String previousAvatarUrl) {
        Asset previous = resolveOwnedAsset(previousAvatarUrl);
        if (previous != null && accountId.equals(previous.getAccountId()) && KIND_AVATAR.equals(previous.getKind())) {
            assetService.delete(previous);
        }
    }

    private Asset resolveOwnedAsset(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return null;
        }
        try {
            Long assetId = Long.valueOf(avatarUrl.substring(AVATAR_URL_PREFIX.length()));
            return assetMapper.selectById(assetId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
