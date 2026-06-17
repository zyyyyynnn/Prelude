package com.interview.dto;

public record UserProfileResponse(
    String username,
    String email,
    String avatarUrl,
    String themePreference
) {
}
