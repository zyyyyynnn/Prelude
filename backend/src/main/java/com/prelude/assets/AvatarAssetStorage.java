package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvatarAssetStorage implements AvatarStoragePort {

    private static final String KIND_AVATAR = "avatar";
    private static final String AVATAR_URL_PREFIX = "/api/assets/";
    private static final String AVATAR_URL_SUFFIX = "/content";

    private final AssetService assetService;
    private final AssetLookup assetLookup;
    private final ObjectStoragePort objectStoragePort;
    private final AvatarImageValidator avatarImageValidator;
    private final AssetDeletionService assetDeletionService;

    @Override
    public String stage(Long accountId, String fileName, String mediaType, byte[] bytes) {
        AvatarImageValidator.ValidatedAvatar validated = avatarImageValidator.validate(
            fileName, mediaType, bytes);
        AssetRow asset = assetService.createPending(
            accountId, KIND_AVATAR, validated.mediaType(), bytes.length);
        try {
            objectStoragePort.put(asset.objectKey(), validated.mediaType(), bytes);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("头像上传失败");
        }
        return AVATAR_URL_PREFIX + asset.id() + AVATAR_URL_SUFFIX;
    }

    @Override
    public void confirmReady(String avatarUrl) {
        AssetRow asset = resolveOwnedAsset(null, avatarUrl);
        if (asset == null || !assetService.markReady(asset.id())) {
            throw BusinessException.badRequest("头像上传失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discard(Long accountId, String avatarUrl) {
        AssetRow asset = resolveOwnedAsset(accountId, avatarUrl);
        if (asset != null) {
            assetDeletionService.register(asset);
        }
    }

    private AssetRow resolveOwnedAsset(Long accountId, String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX) || !avatarUrl.endsWith(AVATAR_URL_SUFFIX)) {
            return null;
        }
        String idSegment = avatarUrl.substring(
            AVATAR_URL_PREFIX.length(), avatarUrl.length() - AVATAR_URL_SUFFIX.length());
        try {
            Long assetId = Long.valueOf(idSegment);
            AssetRow asset = assetLookup.findById(assetId);
            return asset != null
                && (accountId == null || accountId.equals(asset.accountId()))
                && KIND_AVATAR.equals(asset.kind())
                ? asset
                : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
