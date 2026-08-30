package com.prelude.artifact.api;

import java.util.List;

public interface ArtifactQueryApi {

    ArtifactRef findOwnedArtifact(Long accountId, String kind);

    List<ArtifactVersionRef> listVersions(Long accountId, Long artifactId);
}
