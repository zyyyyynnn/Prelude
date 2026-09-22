package com.prelude.llm.application.port;

import java.util.Optional;

/**
 * Model-profile row access. Speaks in the profile's own fields rather than the
 * persistence row type, so no use case names the persistence package.
 */
public interface ModelProfileStore {

    /** The account's active profile, or empty when it has none yet. */
    Optional<ProfileRow> findActiveByAccount(Long accountId);

    /** The account's active profile under a row lock, for a revision-guarded write. */
    Optional<ProfileRow> findActiveForUpdate(Long accountId);

    /** Inserts a profile and reports the row it wrote. */
    ProfileRow insert(ProfileRow profile);

    /** Rewrites the stored profile. */
    void update(ProfileRow profile);

    /** The profile row as the service sees it. */
    record ProfileRow(
        Long id,
        Long accountId,
        String provider,
        String model,
        String customEndpointUrl,
        String reasoningLevel,
        String effectiveParametersJson,
        String modelCapabilityJson,
        String fallbackCapabilitiesJson,
        Long credentialId
    ) {
    }
}
