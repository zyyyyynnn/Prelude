package com.prelude.identity.application.port;

/**
 * An uploaded avatar, reduced to the bytes and metadata a use case validates.
 *
 * <p>Use cases must not name {@code org.springframework.web.multipart.MultipartFile}:
 * that type is an HTTP adapter and drags the servlet stack into the application layer.
 * The web layer adapts the real upload to this projection.
 */
public record AvatarUpload(String fileName, String mediaType, byte[] content) {

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
