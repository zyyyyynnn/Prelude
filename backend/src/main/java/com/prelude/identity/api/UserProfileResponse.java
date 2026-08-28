package com.prelude.identity.api;

public record UserProfileResponse(
    String username,
    String email,
    String avatarUrl,
    String themePreference
) {
}
