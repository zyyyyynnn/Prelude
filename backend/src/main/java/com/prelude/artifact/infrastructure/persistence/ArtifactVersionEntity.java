package com.prelude.artifact.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.artifact.domain.ArtifactVersion;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code artifact_version}; keeps the table mapping out of the domain model. */
@Data
@TableName("artifact_version")
public class ArtifactVersionEntity {

    private Long id;
    private Long artifactId;
    private Integer versionNumber;
    private Long assetId;
    private String provenanceJson;
    private LocalDateTime createdAt;

    public static ArtifactVersionEntity of(ArtifactVersion version) {
        ArtifactVersionEntity entity = new ArtifactVersionEntity();
        entity.setId(version.getId());
        entity.setArtifactId(version.getArtifactId());
        entity.setVersionNumber(version.getVersionNumber());
        entity.setAssetId(version.getAssetId());
        entity.setProvenanceJson(version.getProvenanceJson());
        entity.setCreatedAt(version.getCreatedAt());
        return entity;
    }

    public ArtifactVersion toDomain() {
        ArtifactVersion version = new ArtifactVersion();
        version.setId(id);
        version.setArtifactId(artifactId);
        version.setVersionNumber(versionNumber);
        version.setAssetId(assetId);
        version.setProvenanceJson(provenanceJson);
        version.setCreatedAt(createdAt);
        return version;
    }
}
