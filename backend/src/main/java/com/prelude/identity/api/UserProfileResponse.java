package com.prelude.identity.api;

public record UserProfileResponse(
    Long accountId,
    String username,
    String email,
    String avatarUrl,
    String themePreference,
    long revision
) {
}
