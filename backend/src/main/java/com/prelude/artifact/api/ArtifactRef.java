package com.prelude.artifact.api;

/**
 * Reference to a formal artifact owned by an account.
 */
public record ArtifactRef(Long artifactId, String kind) {
}
