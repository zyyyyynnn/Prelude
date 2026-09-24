package com.prelude.assets;

import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import com.prelude.identity.application.ProfileService;
import com.prelude.test.AccountFixtures;
import com.prelude.test.AssetFixtures;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AssetEntity lifecycle against real MySQL and the S3-compatible endpoint:
 * PENDING_UPLOAD to READY, controlled reads, ownership checks and reconciler cleanup.
 * The VersityGW lifecycle is owned by this test via Testcontainers.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class AssetLifecycleTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> VERSITYGW = new GenericContainer<>("versity/versitygw:v1.7.0")
        .withEnv("ROOT_ACCESS_KEY", "prelude-local-key")
        .withEnv("ROOT_SECRET_KEY", "prelude-local-secret")
        .withEnv("VGW_BACKEND", "posix")
        .withEnv("VGW_BACKEND_ARGS", "/data/s3")
        .withEnv("VGW_PORT", ":7070")
        .withEnv("VGW_IAM_DIR", "/data/iam")
        .withEnv("VGW_VERSIONING_DIR", "/data/versioning")
        .withTmpFs(java.util.Map.of(
            "/data/s3", "rw",
            "/data/iam", "rw",
            "/data/versioning", "rw"))
        .withExposedPorts(7070)
        .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void versityGwProperties(DynamicPropertyRegistry registry) {
        VERSITYGW.start();
        String endpoint = "http://" + VERSITYGW.getHost() + ":" + VERSITYGW.getMappedPort(7070);
        registry.add("prelude.storage.s3.endpoint", () -> endpoint);
        registry.add("prelude.storage.s3.public-endpoint", () -> endpoint);
    }

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectStoragePort objectStoragePort;

    @Autowired
    private StalePendingAssetReconciler stalePendingAssetReconciler;

    @Autowired
    private ProfileService profileService;

    @Test
    void uploadMarksTheAssetReadyAndBinaryTruthLivesOnlyInObjectStorage() {
        long accountId = createAccount("asset-owner");
        authenticate(accountId);

        AttachmentServiceSnapshot snapshot = uploadSnapshot(accountId,
            "notes.txt", "text/plain", "attachment body".getBytes(StandardCharsets.UTF_8));

        AssetEntity asset = assetMapper.selectById(snapshot.assetId());
        assertThat(asset.getStatus()).isEqualTo(AssetFixtures.statusReady());
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

        byte[] content = attachmentService.readOwnedContent(accountId, bound.get(0).assetRef());
        assertThat(content).isEqualTo(pngBytes);

        long other = createAccount("asset-image-other");
        ExceptionFixtures.assertBusinessException(
            () -> attachmentService.readOwnedContent(other, bound.get(0).assetRef()),
            "not_found");
    }

    @Test
    void avatarPublicationCommitsAReferenceThatResolvesToTheAuthorizedContentEndpoint() {
        long accountId = createAccount("avatar-owner");
        authenticate(accountId);
        byte[] png = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        MockMultipartFile firstUpload =
            new MockMultipartFile("file", "me.png", "image/png", png);
        MockMultipartFile secondUpload =
            new MockMultipartFile("file", "me-2.png", "image/png", png);

        var first = profileService.updateAvatar(SessionFixtures.avatarUpload(firstUpload));
        assertThat(first.avatarUrl()).startsWith("/api/assets/");
        assertThat(first.avatarUrl()).endsWith("/content");
        long firstAssetId = assetIdFromUrl(first.avatarUrl());
        assertThat(assetMapper.selectById(firstAssetId)).isNotNull();

        var second = profileService.updateAvatar(SessionFixtures.avatarUpload(secondUpload));
        long secondAssetId = assetIdFromUrl(second.avatarUrl());
        assertThat(secondAssetId).isNotEqualTo(firstAssetId);
        assertThat(assetMapper.selectById(secondAssetId)).isNotNull();
        assertThat(assetMapper.selectById(firstAssetId)).isNull();
    }

    private long assetIdFromUrl(String avatarUrl) {
        String id = avatarUrl.substring("/api/assets/".length(), avatarUrl.length() - "/content".length());
        return Long.parseLong(id);
    }

    @Test
    void crossAccountAssetAccessIsNotFoundEquivalent() {
        long owner = createAccount("asset-owner-a");
        long other = createAccount("asset-owner-b");
        authenticate(owner);
        AttachmentServiceSnapshot snapshot = uploadSnapshot(owner,
            "secret.txt", "text/plain", "private".getBytes(StandardCharsets.UTF_8));

        ExceptionFixtures.assertBusinessException(
            () -> assetService.requireOwnedReady(other, snapshot.assetId()),
            "not_found");
        assertThat(assetService.requireOwnedReady(owner, snapshot.assetId())).isNotNull();
    }

    @Test
    void reconcilerRemovesStalePendingAssetsWithTheirObjects() {
        long accountId = createAccount("asset-reconcile");
        authenticate(accountId);
        AttachmentServiceSnapshot snapshot = uploadSnapshot(accountId,
            "orphan.txt", "text/plain", "orphan".getBytes(StandardCharsets.UTF_8));

        AssetEntity asset = assetMapper.selectById(snapshot.assetId());
        asset.setStatus(AssetFixtures.statusPendingUpload());
        asset.setCreatedAt(LocalDateTime.now().minusHours(48));
        assetMapper.updateById(asset);

        stalePendingAssetReconciler.reconcileStalePendingAssets();

        assertThat(assetMapper.selectById(snapshot.assetId())).isNull();
        assertThatThrownBy(() -> objectStoragePort.get(asset.getObjectKey()))
            .isInstanceOf(RuntimeException.class);
    }

    /* Object keys are generated by the store that persists the asset row, so uniqueness is a
       property of the lifecycle, not of the S3 adapter that merely receives a key. Asserting it
       here is what keeps it a real claim: two uploads have to land on two addresses, each
       reading back its own bytes. */
    /* Byte-identical uploads are the case that matters: a key derived from the content would
       pass a differing-content test and still leave one delete orphaning the other asset. */
    @Test
    void successiveUploadsGetTheirOwnObjectKeyEvenWhenTheBytesAreIdentical() {
        long accountId = createAccount("asset-key-owner");
        authenticate(accountId);
        byte[] sameBytes = "the same content".getBytes(StandardCharsets.UTF_8);
        AttachmentServiceSnapshot first = uploadSnapshot(accountId,
            "first.txt", "text/plain", sameBytes);
        AttachmentServiceSnapshot second = uploadSnapshot(accountId,
            "second.txt", "text/plain", sameBytes);

        String firstKey = assetMapper.selectById(first.assetId()).getObjectKey();
        String secondKey = assetMapper.selectById(second.assetId()).getObjectKey();

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(new String(objectStoragePort.get(firstKey), StandardCharsets.UTF_8))
            .isEqualTo("the same content");
        assertThat(new String(objectStoragePort.get(secondKey), StandardCharsets.UTF_8))
            .isEqualTo("the same content");

        attachmentService.deleteUnbound(first.attachmentId());
        assertThat(new String(objectStoragePort.get(secondKey), StandardCharsets.UTF_8))
            .isEqualTo("the same content");
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

    private AttachmentServiceSnapshot uploadSnapshot(long accountId, String name, String mediaType, byte[] bytes) {
        com.prelude.assets.api.AttachmentSnapshot snapshot =
            attachmentService.upload(name, mediaType, bytes);
        return new AttachmentServiceSnapshot(snapshot.id(), snapshot.assetRef().id());
    }

    private record AttachmentServiceSnapshot(long attachmentId, long assetId) {
    }
}
