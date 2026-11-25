package com.prelude.assets;

import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import com.prelude.test.AssetFixtures;
import com.prelude.test.ExceptionFixtures;
import com.prelude.identity.application.AvatarPublication;
import com.prelude.identity.application.ProfileService;
import com.prelude.test.AccountFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    private AssetDeletionService assetDeletionService;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileService profileService;

    @Test
    void failedAttachmentFinalizationKeepsTheAssetPendingForTheReconciler() {
        long accountId = createAccount("finalize-anchor");
        authenticate(accountId);
        doThrow(new IllegalStateException("reference insert failed"))
            .when(attachmentPublication).finalizeUpload(any(), any());

        ExceptionFixtures.assertBusinessExceptionMessage(() -> attachmentService.upload(
            "notes.txt", "text/plain", "body".getBytes(StandardCharsets.UTF_8)), "附件上传失败");

        AssetEntity anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetFixtures.statusPendingUpload());

        anchor.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(anchor);
        stalePendingAssetReconciler.reconcileStalePendingAssets();
        assertThat(assetMapper.selectById(anchor.getId())).isNull();
    }

    @Test
    void failedAvatarFinalizationKeepsTheAssetPendingWhenDiscardAlsoFails() {
        long accountId = createAccount("avatar-finalize-anchor");
        authenticate(accountId);
        doThrow(ExceptionFixtures.revisionConflict("资料已被其他操作更新，请刷新后重试"))
            .when(avatarPublication).publish(anyString(), any(), any());

        ExceptionFixtures.assertBusinessException(
            () -> profileService.updateAvatar(SessionFixtures.avatarUpload(avatarFile())), "revision_conflict");

        AssetEntity anchor = latestPendingAsset(accountId);
        assertThat(anchor).isNotNull();
        assertThat(anchor.getStatus()).isEqualTo(AssetFixtures.statusPendingUpload());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM asset_deletion_request WHERE asset_id = ?", String.class, anchor.getId()))
            .isEqualTo("PENDING");
        verify(objectStoragePort, never()).delete(anyString());

        doThrow(new IllegalStateException("gateway down"))
            .doNothing()
            .when(objectStoragePort).delete(anchor.getObjectKey());
        assetDeletionService.processNextBatch();
        assertThat(assetMapper.selectById(anchor.getId())).isNotNull();

        jdbcTemplate.update("UPDATE asset_deletion_request SET next_attempt_at = ? WHERE asset_id = ?",
            LocalDateTime.now().minusSeconds(1), anchor.getId());
        assetDeletionService.processNextBatch();
        assertThat(assetMapper.selectById(anchor.getId())).isNull();
    }

    private long createAccount(String prefix) {
        return AccountFixtures.create(jdbcTemplate, prefix);
    }

    @AfterEach
    void forgetTheAuthenticatedAccount() {
        AccountFixtures.clearAuthentication();
    }

    private void authenticate(long accountId) {
        AccountFixtures.authenticate(accountId);
    }

    private AssetEntity latestPendingAsset(long accountId) {
        return assetMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetEntity>()
                .eq(AssetEntity::getAccountId, accountId)
                .eq(AssetEntity::getStatus, AssetFixtures.statusPendingUpload())
                .orderByDesc(AssetEntity::getId)
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private MockMultipartFile avatarFile() {
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4AWJiYGD4DwAAAP//cGajQwAAAAZJREFUAwABDgEC81VxbAAAAABJRU5ErkJggg==");
        return new MockMultipartFile("file", "me.png", "image/png", png);
    }
}
