package com.prelude.artifact.application;

import com.prelude.BusinessException;
import com.prelude.artifact.api.ArtifactCommandApi;
import com.prelude.artifact.api.ArtifactQueryApi;
import com.prelude.artifact.api.ArtifactVersionRef;
import com.prelude.artifact.domain.ArtifactVersion;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Artifact publication: versioned, immutable, account-owned, asset-validated.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class ArtifactPublishServiceTest {

    @Autowired
    private ArtifactPublishService artifactPublishService;

    @Autowired
    private ArtifactQueryApi artifactQueryApi;

    @Autowired
    private com.prelude.artifact.persistence.ArtifactVersionMapper artifactVersionMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AssetMapper assetMapper;

    @Test
    void repeatedPublishingCreatesIncreasingImmutableVersions() {
        long accountId = createAccount();
        long assetId = createReadyAsset(accountId);

        ArtifactVersionRef first = artifactPublishService.publishVersion(
            new ArtifactCommandApi.PublishVersionCommand(accountId, "interview-report", assetId, "{\"rev\":1}"));
        ArtifactVersionRef second = artifactPublishService.publishVersion(
            new ArtifactCommandApi.PublishVersionCommand(accountId, "interview-report", assetId, "{\"rev\":2}"));

        assertThat(first.versionNumber()).isEqualTo(1);
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.versionId()).isNotEqualTo(first.versionId());

        var versions = artifactQueryApi.listVersions(accountId, first.artifactId());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionNumber()).isEqualTo(1);
        assertThat(versions.get(0).asset().assetId()).isEqualTo(assetId);

        ArtifactVersion storedFirst = artifactVersionMapper.selectById(first.versionId());
        assertThat(storedFirst.getVersionNumber()).isEqualTo(1);
        assertThat(storedFirst.getProvenanceJson()).isEqualTo("{\"rev\":1}");
    }

    @Test
    void publishingValidatesAssetOwnershipBeforeCreatingVersions() {
        long owner = createAccount();
        long other = createAccount();
        long assetId = createReadyAsset(owner);

        assertThatThrownBy(() -> artifactPublishService.publishVersion(
            new ArtifactCommandApi.PublishVersionCommand(other, "interview-report", assetId, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "not_found");

        assertThatThrownBy(() -> artifactPublishService.publishVersion(
            new ArtifactCommandApi.PublishVersionCommand(owner, "interview-report", 999_999L, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "not_found");
    }

    private long createAccount() {
        Account account = new Account();
        account.setUsername("artifact-" + UUID.randomUUID());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
    }

    private long createReadyAsset(long accountId) {
        Asset asset = new Asset();
        asset.setAccountId(accountId);
        asset.setKind("report");
        asset.setObjectKey(UUID.randomUUID().toString());
        asset.setMediaType("application/pdf");
        asset.setByteSize(128L);
        asset.setStatus(AssetStatus.READY);
        assetMapper.insert(asset);
        return asset.getId();
    }
}
