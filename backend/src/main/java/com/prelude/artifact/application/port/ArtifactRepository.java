package com.prelude.artifact.application.port;

import com.prelude.artifact.domain.Artifact;
import com.prelude.artifact.domain.ArtifactVersion;

import java.util.List;

public interface ArtifactRepository {

    /** The account's artifact of this kind, or null when it has none yet. */
    Artifact find(Long accountId, String kind);

    Artifact findById(Long artifactId);

    /** Persists a new artifact and writes the generated identifier back. */
    void add(Artifact artifact);

    /** Persists a version; a concurrent claim of the same number raises a duplicate key. */
    void addVersion(ArtifactVersion version);

    int highestVersionNumber(Long artifactId);

    List<ArtifactVersion> listVersions(Long artifactId);
}
