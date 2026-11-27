package com.prelude.artifact.api;

import java.util.List;

/**
 * Reserved public query contract for formal artifacts. No production consumer is
 * currently wired; the implementation and artifact tables remain in place.
 */
public interface ArtifactQueryApi {

    ArtifactRef findOwnedArtifact(Long accountId, String kind);

    List<ArtifactVersionRef> listVersions(Long accountId, Long artifactId);
}
