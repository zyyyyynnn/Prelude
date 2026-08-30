package com.prelude.artifact.api;

/**
 * Reference to an immutable committed version of an artifact.
 */
public record ArtifactVersionRef(
    Long artifactId,
    Long versionId,
    int versionNumber,
    AssetRefView asset
) {
}
