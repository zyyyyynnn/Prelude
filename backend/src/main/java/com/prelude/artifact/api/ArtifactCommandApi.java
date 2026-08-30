package com.prelude.artifact.api;

/**
 * Public command contract of the artifact module. Formal results are only
 * published through this API; other modules never write artifact tables.
 */
public interface ArtifactCommandApi {

    /**
     * Publishes the next immutable version of the artifact identified by
     * (accountId, kind), creating the artifact on first publish.
     */
    ArtifactVersionRef publishVersion(PublishVersionCommand command);

    record PublishVersionCommand(
        Long accountId,
        String kind,
        Long assetId,
        String provenanceJson
    ) {
    }
}
