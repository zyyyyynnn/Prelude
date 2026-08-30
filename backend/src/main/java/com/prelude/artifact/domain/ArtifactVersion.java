package com.prelude.artifact.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Immutable committed version of an artifact. Publishing new content always
 * creates the next version; existing versions are never updated.
 */
@Data
public class ArtifactVersion {

    private Long id;
    private Long artifactId;
    private Integer versionNumber;
    private Long assetId;
    private String provenanceJson;
    private LocalDateTime createdAt;
}
