package com.prelude.llm.application.port;

import com.prelude.llm.api.LlmPort.FrozenModelConfiguration;

/**
 * Model-execution-snapshot row access. A snapshot is what a run reads its
 * configuration from, so writes must be a single insert and reads must return the
 * frozen configuration rather than the live profile.
 */
public interface ModelExecutionSnapshotStore {

    /** Inserts a frozen snapshot and reports the id it was given. */
    Long insert(SnapshotRow snapshot);

    /** The snapshot, or null when no snapshot carries that id. */
    SnapshotRow findById(Long snapshotId);

    /** The snapshot as the freeze path writes it. */
    record SnapshotRow(
        Long id,
        Long accountId,
        Long profileId,
        String provider,
        String model,
        String reasoningLevel,
        String effectiveParametersJson,
        String capabilityVersion,
        String modelCapabilityJson,
        String fallbackCapabilitiesJson,
        Long credentialId,
        String customEndpointUrl
    ) {

        /** A copy retargeted at a fallback model, with its capability frozen in. */
        public SnapshotRow withModel(String model, String modelCapabilityJson) {
            return new SnapshotRow(
                id, accountId, profileId, provider, model, reasoningLevel,
                effectiveParametersJson, capabilityVersion, modelCapabilityJson,
                fallbackCapabilitiesJson, credentialId, customEndpointUrl);
        }
    }
}
