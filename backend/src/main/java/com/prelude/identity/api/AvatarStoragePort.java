package com.prelude.identity.api;

/**
 * Port for staged avatar publication, implemented by the assets module.
 * identity never touches object storage infrastructure directly.
 *
 * Lifecycle: stage() creates a PENDING asset with its remote object and
 * returns the candidate reference; confirmReady() must be called inside the
 * same DB transaction that commits the account reference, so a failure of
 * either rolls the account update and the READY transition back together and
 * leaves the asset PENDING for the reconciler.
 */
public interface AvatarStoragePort {

    /**
     * Creates a PENDING avatar asset and uploads its object. Returns the
     * candidate reference URL that resolves to the authorized content
     * endpoint (/api/assets/{id}/content). Storage failures leave the
     * PENDING row as the recovery anchor.
     */
    String stage(Long accountId, String mediaType, byte[] bytes);

    /**
     * Moves the staged avatar asset from PENDING_UPLOAD to READY. Must run
     * inside the finalization transaction of the account reference.
     */
    void confirmReady(String avatarUrl);

    /**
     * Best-effort removal of one avatar asset owned by the account. Storage or
     * database failures propagate; callers treat cleanup as non-fatal.
     */
    void discard(Long accountId, String avatarUrl);
}
