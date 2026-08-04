package com.interview.identity.application.port;

public record ProcessedAvatar(
    byte[] bytes,
    String contentType,
    String extension,
    int width,
    int height
) {

    public ProcessedAvatar {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Processed avatar bytes are required");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
