package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.UserProfileResponse;
import com.prelude.identity.application.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Failure-path contracts for the asset lifecycle: every remote failure must
 * leave a recovery anchor behind instead of losing cleanup state.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class AssetStorageFailureTest {

    @MockitoBean
    private ObjectStoragePort objectStoragePort;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private StalePendingAssetReconciler stalePendingAssetReconciler;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private ProfileService profileService;

    @Test
    void failedObjectPutKeepsThePendingUploadRecoveryAnchor() {
        long accountId = createAccount("upload-anchor");
        authenticate(accountId);
        doThrow(new IllegalStateException("gateway down"))
            .when(objectStoragePort).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> attachmentService.upload(
            "notes.txt", "text/plain", "body".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("附件上传失败");

        Asset anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetStatus.PENDING_UPLOAD);
    }

    @Test
    void failedAvatarPutKeepsThePendingUploadRecoveryAnchor() {
        long accountId = createAccount("avatar-anchor");
        authenticate(accountId);
        doThrow(new IllegalStateException("gateway down"))
            .when(objectStoragePort).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> profileService.updateAvatar(avatarFile()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("头像上传失败");

        Asset anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetStatus.PENDING_UPLOAD);
    }

    @Test
    void failedObjectDeleteKeepsTheAssetRowAsRecoveryAnchor() {
        long accountId = createAccount("delete-anchor");
        authenticate(accountId);
        Asset asset = createReadyAsset(accountId);
        doThrow(new IllegalStateException("gateway down"))
            .when(objectStoragePort).delete(asset.getObjectKey());

        assertThatThrownBy(() -> assetService.delete(asset))
            .isInstanceOf(IllegalStateException.class);

        assertThat(assetMapper.selectById(asset.getId())).isNotNull();
    }

    @Test
    void reconcilerKeepsTheRowWhenObjectDeletionFailsAndRemovesItOnTheNextPass() {
        long accountId = createAccount("reconcile-anchor");
        authenticate(accountId);
        Asset asset = createReadyAsset(accountId);
        asset.setStatus(AssetStatus.PENDING_UPLOAD);
        asset.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(asset);

        doThrow(new IllegalStateException("gateway down"))
            .doNothing()
            .when(objectStoragePort).delete(asset.getObjectKey());

        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(asset.getId())).isNotNull();

        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(asset.getId())).isNull();
    }

    private long createAccount(String prefix) {
        Account account = new Account();
        account.setUsername(prefix + "-" + UUID.randomUUID());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private void authenticate(long accountId) {
        AccountPrincipal principal = new AccountPrincipal(accountId, "tester");
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private Asset createReadyAsset(long accountId) {
        Asset asset = new Asset();
        asset.setAccountId(accountId);
        asset.setKind("attachment");
        asset.setObjectKey(UUID.randomUUID().toString());
        asset.setMediaType("text/plain");
        asset.setByteSize(6L);
        asset.setStatus(AssetStatus.READY);
        assetMapper.insert(asset);
        return asset;
    }

    private Asset latestPendingAsset(long accountId) {
        return assetMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Asset>()
                .eq(Asset::getAccountId, accountId)
                .eq(Asset::getStatus, AssetStatus.PENDING_UPLOAD)
                .orderByDesc(Asset::getId)
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private MockMultipartFile avatarFile() {
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        return new MockMultipartFile("file", "me.png", "image/png", png);
    }
}
