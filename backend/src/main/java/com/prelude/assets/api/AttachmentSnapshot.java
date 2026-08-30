package com.prelude.assets.api;

public record AttachmentSnapshot(
    Long id,
    String fileName,
    String mediaType,
    long size,
    boolean image,
    String text,
    byte[] content
) {
}
