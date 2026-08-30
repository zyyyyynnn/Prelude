package com.prelude.identity.api;

/**
 * Port for storing the account avatar binary. Implemented by the assets module;
 * identity never touches object storage infrastructure directly.
 */
public interface AvatarStoragePort {

    /**
     * Stores the new avatar and returns the public reference URL for user_account.avatar_url.
     */
    String store(Long accountId, String previousAvatarUrl, String mediaType, byte[] bytes);

    /**
     * Best-effort removal of an avatar asset that was stored but not committed.
     */
    void discard(String avatarUrl);
}
