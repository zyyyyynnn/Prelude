package com.prelude.artifact.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.artifact.application.port.ArtifactRepository;
import com.prelude.artifact.domain.Artifact;
import com.prelude.artifact.domain.ArtifactVersion;
import com.prelude.artifact.infrastructure.persistence.ArtifactEntity;
import com.prelude.artifact.infrastructure.persistence.ArtifactMapper;
import com.prelude.artifact.infrastructure.persistence.ArtifactVersionEntity;
import com.prelude.artifact.infrastructure.persistence.ArtifactVersionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisArtifactRepository implements ArtifactRepository {

    private final ArtifactMapper artifactMapper;
    private final ArtifactVersionMapper artifactVersionMapper;

    @Override
    public Artifact find(Long accountId, String kind) {
        ArtifactEntity entity = artifactMapper.selectOne(new LambdaQueryWrapper<ArtifactEntity>()
            .eq(ArtifactEntity::getAccountId, accountId)
            .eq(ArtifactEntity::getKind, kind)
            .last("LIMIT 1"));
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public Artifact findById(Long artifactId) {
        ArtifactEntity entity = artifactMapper.selectById(artifactId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public void add(Artifact artifact) {
        ArtifactEntity entity = ArtifactEntity.of(artifact);
        artifactMapper.insert(entity);
        artifact.setId(entity.getId());
    }

    @Override
    public void addVersion(ArtifactVersion version) {
        ArtifactVersionEntity entity = ArtifactVersionEntity.of(version);
        artifactVersionMapper.insert(entity);
        version.setId(entity.getId());
    }

    @Override
    public int highestVersionNumber(Long artifactId) {
        return artifactVersionMapper.highestVersionNumber(artifactId);
    }

    @Override
    public List<ArtifactVersion> listVersions(Long artifactId) {
        return artifactVersionMapper.selectList(new LambdaQueryWrapper<ArtifactVersionEntity>()
            .eq(ArtifactVersionEntity::getArtifactId, artifactId)
            .orderByAsc(ArtifactVersionEntity::getVersionNumber))
            .stream()
            .map(ArtifactVersionEntity::toDomain)
            .toList();
    }
}
