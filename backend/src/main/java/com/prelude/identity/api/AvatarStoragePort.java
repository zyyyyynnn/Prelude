package com.prelude.identity.api;

/**
 * Port for storing the account avatar binary. Implemented by the assets module;
 * identity never touches object storage infrastructure directly.
 *
 * Storing never touches the previous avatar: the caller commits the guarded
 * account reference first and only then cleans up the obsolete asset.
 */
public interface AvatarStoragePort {

    /**
     * Stores a new avatar asset and returns its public reference URL, which
     * resolves to the authorized content endpoint (/api/assets/{id}/content).
     */
    String store(Long accountId, String mediaType, byte[] bytes);

    /**
     * Best-effort removal of one avatar asset owned by the account. Storage or
     * database failures propagate; callers treat cleanup as non-fatal.
     */
    void discard(Long accountId, String avatarUrl);
}
