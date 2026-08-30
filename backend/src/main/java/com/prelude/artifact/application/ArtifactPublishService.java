package com.prelude.artifact.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.artifact.api.ArtifactCommandApi;
import com.prelude.artifact.api.ArtifactQueryApi;
import com.prelude.artifact.api.ArtifactRef;
import com.prelude.artifact.api.ArtifactVersionRef;
import com.prelude.artifact.api.AssetRefView;
import com.prelude.artifact.domain.Artifact;
import com.prelude.artifact.domain.ArtifactVersion;
import com.prelude.artifact.persistence.ArtifactMapper;
import com.prelude.artifact.persistence.ArtifactVersionMapper;
import com.prelude.assets.api.AssetQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The single publication entry for formal results. Version numbers come from
 * the database unique constraint; concurrent publishers retry deterministically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactPublishService implements ArtifactCommandApi, ArtifactQueryApi {

    private static final int MAX_VERSION_ASSIGNMENT_ATTEMPTS = 5;

    private final ArtifactMapper artifactMapper;
    private final ArtifactVersionMapper artifactVersionMapper;
    private final AssetQueryApi assetQueryApi;

    @Override
    public ArtifactVersionRef publishVersion(PublishVersionCommand command) {
        if (command.accountId() == null || command.kind() == null || command.kind().isBlank()) {
            throw BusinessException.badRequest("成果发布参数不完整");
        }
        if (command.assetId() != null) {
            assetQueryApi.requireOwnedReadyAsset(command.accountId(), command.assetId());
        }
        Artifact artifact = requireOrCreateArtifact(command.accountId(), command.kind());
        ArtifactVersion version = insertNextVersion(artifact.getId(), command);
        return toVersionRef(version);
    }

    @Override
    public ArtifactRef findOwnedArtifact(Long accountId, String kind) {
        Artifact artifact = artifactMapper.selectOne(new LambdaQueryWrapper<Artifact>()
            .eq(Artifact::getAccountId, accountId)
            .eq(Artifact::getKind, kind)
            .last("LIMIT 1"));
        return artifact == null ? null : new ArtifactRef(artifact.getId(), artifact.getKind());
    }

    @Override
    public List<ArtifactVersionRef> listVersions(Long accountId, Long artifactId) {
        Artifact artifact = artifactMapper.selectById(artifactId);
        if (artifact == null || !artifact.getAccountId().equals(accountId)) {
            throw BusinessException.notFound("成果不存在");
        }
        return artifactVersionMapper.selectList(new LambdaQueryWrapper<ArtifactVersion>()
                .eq(ArtifactVersion::getArtifactId, artifactId)
                .orderByAsc(ArtifactVersion::getVersionNumber))
            .stream()
            .map(this::toVersionRef)
            .toList();
    }

    private Artifact requireOrCreateArtifact(Long accountId, String kind) {
        Artifact artifact = artifactMapper.selectOne(new LambdaQueryWrapper<Artifact>()
            .eq(Artifact::getAccountId, accountId)
            .eq(Artifact::getKind, kind)
            .last("LIMIT 1"));
        if (artifact != null) {
            return artifact;
        }
        Artifact created = new Artifact();
        created.setAccountId(accountId);
        created.setKind(kind);
        try {
            artifactMapper.insert(created);
            return created;
        } catch (DuplicateKeyException duplicate) {
            return artifactMapper.selectOne(new LambdaQueryWrapper<Artifact>()
                .eq(Artifact::getAccountId, accountId)
                .eq(Artifact::getKind, kind)
                .last("LIMIT 1"));
        }
    }

    private ArtifactVersion insertNextVersion(Long artifactId, PublishVersionCommand command) {
        for (int attempt = 0; attempt < MAX_VERSION_ASSIGNMENT_ATTEMPTS; attempt++) {
            Integer maxVersion = artifactVersionMapper.selectList(new LambdaQueryWrapper<ArtifactVersion>()
                    .eq(ArtifactVersion::getArtifactId, artifactId))
                .stream()
                .map(ArtifactVersion::getVersionNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
            ArtifactVersion version = new ArtifactVersion();
            version.setArtifactId(artifactId);
            version.setVersionNumber(maxVersion + 1);
            version.setAssetId(command.assetId());
            version.setProvenanceJson(command.provenanceJson());
            try {
                artifactVersionMapper.insert(version);
                return version;
            } catch (DuplicateKeyException duplicate) {
                log.info("Concurrent version creation for artifact {}; retrying", artifactId);
            }
        }
        throw BusinessException.badRequest("成果版本创建冲突，请重试");
    }

    private ArtifactVersionRef toVersionRef(ArtifactVersion version) {
        return new ArtifactVersionRef(
            version.getArtifactId(),
            version.getId(),
            version.getVersionNumber(),
            version.getAssetId() == null ? null : new AssetRefView(version.getAssetId())
        );
    }
}
