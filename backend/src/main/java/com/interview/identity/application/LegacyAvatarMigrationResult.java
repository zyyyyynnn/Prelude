package com.interview.identity.application;

public enum LegacyAvatarMigrationResult {
    MIGRATED,
    MISSING,
    INVALID_KEY,
    INVALID,
    UNSUPPORTED_WEBP,
    STALE,
    FAILED
}
