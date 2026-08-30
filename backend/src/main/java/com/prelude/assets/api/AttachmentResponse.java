package com.prelude.assets.api;

public record AttachmentResponse(
    Long id,
    String fileName,
    String mediaType,
    long size,
    boolean image
) {

    static AttachmentResponse from(AttachmentSnapshot attachment) {
        return new AttachmentResponse(
            attachment.id(), attachment.fileName(), attachment.mediaType(),
            attachment.size(), attachment.image()
        );
    }
}
