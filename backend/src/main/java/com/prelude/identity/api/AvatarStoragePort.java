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
     * Validates the image, creates a PENDING asset with its canonical MIME type,
     * and uploads the object. Invalid images create no asset; storage failures
     * leave the PENDING row as the reconciler's recovery anchor.
     */
    String stage(Long accountId, String fileName, String mediaType, byte[] bytes);

    /**
     * Moves the staged avatar asset from PENDING_UPLOAD to READY. Must run
     * inside the finalization transaction of the account reference.
     */
    void confirmReady(String avatarUrl);

    /**
     * Registers durable deletion intent for one avatar asset owned by the account.
     * Joins the caller's transaction when present; publication failures roll back both
     * the account reference and this deletion intent.
     */
    void discard(Long accountId, String avatarUrl);
}
