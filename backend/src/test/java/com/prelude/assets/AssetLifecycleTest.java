package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.assets.persistence.StoredAttachment;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asset lifecycle against real MySQL and the S3-compatible endpoint:
 * PENDING_UPLOAD to READY, controlled reads, ownership checks and reconciler cleanup.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@EnabledIfEnvironmentVariable(named = "PRELUDE_S3_SMOKE", matches = "true")
@SpringBootTest
class AssetLifecycleTest {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private ObjectStoragePort objectStoragePort;

    @Autowired
    private StalePendingAssetReconciler stalePendingAssetReconciler;

    @Test
    void uploadMarksTheAssetReadyAndBinaryTruthLivesOnlyInObjectStorage() {
        long accountId = createAccount("asset-owner");
        authenticate(accountId);

        AttachmentServiceSnapshot snapshot = uploadSnapshot(accountId,
            "notes.txt", "text/plain", "attachment body".getBytes(StandardCharsets.UTF_8));

        Asset asset = assetMapper.selectById(snapshot.assetId());
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(new String(objectStoragePort.get(asset.getObjectKey()), StandardCharsets.UTF_8))
            .isEqualTo("attachment body");

        attachmentService.deleteUnbound(snapshot.attachmentId());
        assertThat(assetMapper.selectById(snapshot.assetId())).isNull();
        assertThatThrownBy(() -> objectStoragePort.get(asset.getObjectKey()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void imageAttachmentsExposeContentOnlyThroughTheControlledRead() {
        long accountId = createAccount("asset-image-owner");
        authenticate(accountId);
        byte[] pngBytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        AttachmentServiceSnapshot snapshot = uploadSnapshot(accountId,
            "pixel.png", "image/png", pngBytes);

        attachmentService.bind(accountId, List.of(snapshot.attachmentId()), "interview", 42L);
        var bound = attachmentService.list(accountId, "interview", 42L);
        assertThat(bound).hasSize(1);
        assertThat(bound.get(0).image()).isTrue();
        assertThat(bound.get(0).assetRef().id()).isEqualTo(snapshot.assetId());

        byte[] content = attachmentService.readContent(bound.get(0).assetRef());
        assertThat(content).isEqualTo(pngBytes);
    }

    @Test
    void crossAccountAssetAccessIsNotFoundEquivalent() {
        long owner = createAccount("asset-owner-a");
        long other = createAccount("asset-owner-b");
        authenticate(owner);
        AttachmentServiceSnapshot snapshot = uploadSnapshot(owner,
            "secret.txt", "text/plain", "private".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> assetService.requireOwnedReady(other, snapshot.assetId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "not_found");
        assertThat(assetService.requireOwnedReady(owner, snapshot.assetId())).isNotNull();
    }

    @Test
    void reconcilerRemovesStalePendingAssetsWithTheirObjects() {
        long accountId = createAccount("asset-reconcile");
        authenticate(accountId);
        AttachmentServiceSnapshot snapshot = uploadSnapshot(accountId,
            "orphan.txt", "text/plain", "orphan".getBytes(StandardCharsets.UTF_8));

        Asset asset = assetMapper.selectById(snapshot.assetId());
        asset.setStatus(AssetStatus.PENDING_UPLOAD);
        asset.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(asset);

        stalePendingAssetReconciler.reconcileStalePendingAssets();

        assertThat(assetMapper.selectById(snapshot.assetId())).isNull();
        assertThatThrownBy(() -> objectStoragePort.get(asset.getObjectKey()))
            .isInstanceOf(RuntimeException.class);
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

    private AttachmentServiceSnapshot uploadSnapshot(long accountId, String name, String mediaType, byte[] bytes) {
        com.prelude.assets.api.AttachmentSnapshot snapshot =
            attachmentService.upload(name, mediaType, bytes);
        return new AttachmentServiceSnapshot(snapshot.id(), snapshot.assetRef().id());
    }

    private record AttachmentServiceSnapshot(long attachmentId, long assetId) {
    }
}
