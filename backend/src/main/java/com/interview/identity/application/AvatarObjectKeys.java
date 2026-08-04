package com.interview.identity.application;

import com.interview.shared.api.BusinessException;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AvatarObjectKeys {

    private static final String MEDIA_PREFIX = "/media/avatars/";
    private static final String LEGACY_PREFIX = "/uploads/avatars/";
    private static final Pattern SAFE_OBJECT_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,254}");

    private AvatarObjectKeys() {
    }

    public static String forUser(long userId, String extension) {
        return userId + "_" + UUID.randomUUID() + "." + extension;
    }

    public static String requireSafe(String objectKey) {
        if (objectKey == null || !SAFE_OBJECT_KEY.matcher(objectKey).matches()
            || objectKey.contains("..")) {
            throw BusinessException.badRequest("头像资源标识不合法");
        }
        return objectKey;
    }

    public static Optional<String> fromStoredUri(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return Optional.empty();
        }
        String objectKey = null;
        if (avatarUrl.startsWith(MEDIA_PREFIX)) {
            objectKey = avatarUrl.substring(MEDIA_PREFIX.length());
        } else if (avatarUrl.startsWith(LEGACY_PREFIX)) {
            objectKey = avatarUrl.substring(LEGACY_PREFIX.length());
        }
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(requireSafe(objectKey));
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    public static String toCanonicalUri(String avatarUrl) {
        return fromStoredUri(avatarUrl)
            .map(objectKey -> MEDIA_PREFIX + objectKey)
            .orElse(avatarUrl);
    }
}
