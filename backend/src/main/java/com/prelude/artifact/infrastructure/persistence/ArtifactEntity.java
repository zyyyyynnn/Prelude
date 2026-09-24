package com.prelude.artifact.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.artifact.domain.Artifact;
import lombok.Data;

import java.time.LocalDateTime;

/** Row shape of {@code artifact}; keeps the table mapping out of the domain model. */
@Data
@TableName("artifact")
public class ArtifactEntity {

    private Long id;
    private Long accountId;
    private String kind;
    private LocalDateTime createdAt;

    public static ArtifactEntity of(Artifact artifact) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setId(artifact.getId());
        entity.setAccountId(artifact.getAccountId());
        entity.setKind(artifact.getKind());
        entity.setCreatedAt(artifact.getCreatedAt());
        return entity;
    }

    public Artifact toDomain() {
        Artifact artifact = new Artifact();
        artifact.setId(id);
        artifact.setAccountId(accountId);
        artifact.setKind(kind);
        artifact.setCreatedAt(createdAt);
        return artifact;
    }
}
