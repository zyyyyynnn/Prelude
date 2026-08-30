package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.application.AvatarPublication;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Failure-path contracts for asset publication: a successful S3 put followed
 * by a failed business-reference finalization must leave the asset PENDING so
 * the bounded reconciler can reclaim it. No READY orphan may exist.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class AssetStorageFailureTest {

    @MockitoBean
    private ObjectStoragePort objectStoragePort;

    @MockitoBean
    private AttachmentPublication attachmentPublication;

    @MockitoBean
    private AvatarPublication avatarPublication;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private StalePendingAssetReconciler stalePendingAssetReconciler;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private ProfileService profileService;

    @Test
    void failedAttachmentFinalizationKeepsTheAssetPendingForTheReconciler() {
        long accountId = createAccount("finalize-anchor");
        authenticate(accountId);
        doThrow(new IllegalStateException("reference insert failed"))
            .when(attachmentPublication).finalizeUpload(any(), any());

        assertThatThrownBy(() -> attachmentService.upload(
            "notes.txt", "text/plain", "body".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("附件上传失败");

        Asset anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetStatus.PENDING_UPLOAD);

        // The reconciler reclaims the anchor: object delete + metadata delete.
        anchor.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(anchor);
        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(anchor.getId())).isNull();
    }

    @Test
    void failedAvatarFinalizationKeepsTheAssetPendingWhenDiscardAlsoFails() {
        long accountId = createAccount("avatar-finalize-anchor");
        authenticate(accountId);
        doThrow(BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试"))
            .when(avatarPublication).publish(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
        doThrow(new IllegalStateException("gateway down"))
            .when(objectStoragePort).delete(anyString());

        assertThatThrownBy(() -> profileService.updateAvatar(avatarFile()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "revision_conflict");

        Asset anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetStatus.PENDING_UPLOAD);

        // Next reconciler pass: the object delete finally succeeds and reclaims the anchor.
        // (Discard consumed the first throw; give the reconciler one failing pass, then success.)
        doThrow(new IllegalStateException("gateway down"))
            .doNothing()
            .doNothing()
            .when(objectStoragePort).delete(anyString());
        anchor.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(anchor);
        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(anchor.getId())).isNotNull();

        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(anchor.getId())).isNull();
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
