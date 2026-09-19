package com.prelude.artifact.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.artifact.application.port.ArtifactRepository;
import com.prelude.artifact.domain.Artifact;
import com.prelude.artifact.domain.ArtifactVersion;
import com.prelude.artifact.infrastructure.persistence.ArtifactMapper;
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
        return artifactMapper.selectOne(new LambdaQueryWrapper<Artifact>()
            .eq(Artifact::getAccountId, accountId)
            .eq(Artifact::getKind, kind)
            .last("LIMIT 1"));
    }

    @Override
    public Artifact findById(Long artifactId) {
        return artifactMapper.selectById(artifactId);
    }

    @Override
    public void add(Artifact artifact) {
        artifactMapper.insert(artifact);
    }

    @Override
    public void addVersion(ArtifactVersion version) {
        artifactVersionMapper.insert(version);
    }

    @Override
    public int highestVersionNumber(Long artifactId) {
        return artifactVersionMapper.highestVersionNumber(artifactId);
    }

    @Override
    public List<ArtifactVersion> listVersions(Long artifactId) {
        return artifactVersionMapper.selectList(new LambdaQueryWrapper<ArtifactVersion>()
            .eq(ArtifactVersion::getArtifactId, artifactId)
            .orderByAsc(ArtifactVersion::getVersionNumber));
    }
}
