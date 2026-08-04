package com.interview.identity.application.port;

import java.io.InputStream;

/**
 * Application-facing upload data. The HTTP MultipartFile stays at the API boundary.
 */
public record AvatarUpload(
    String originalFilename,
    String contentType,
    long contentLength,
    InputStream content
) {

    public AvatarUpload {
        if (content == null) {
            throw new IllegalArgumentException("Avatar content is required");
        }
    }
}
