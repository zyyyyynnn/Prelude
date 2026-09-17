package com.prelude.artifact.application;

import com.prelude.BusinessException;
import com.prelude.artifact.api.ArtifactCommandApi;
import com.prelude.artifact.api.ArtifactQueryApi;
import com.prelude.artifact.api.ArtifactVersionRef;
import com.prelude.artifact.domain.ArtifactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
    private JdbcTemplate jdbcTemplate;

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
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO user_account (username, revision) VALUES (?, 0)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "artifact-" + UUID.randomUUID());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private long createReadyAsset(long accountId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO asset (account_id, kind, object_key, media_type, byte_size, status) VALUES (?, 'report', ?, 'application/pdf', 128, 'READY')",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, accountId);
            ps.setString(2, UUID.randomUUID().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }
}
