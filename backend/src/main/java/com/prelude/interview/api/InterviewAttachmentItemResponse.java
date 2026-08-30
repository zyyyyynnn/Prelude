package com.prelude.interview.api;

public record InterviewAttachmentItemResponse(
    Long id,
    String fileName,
    String mediaType,
    long size,
    boolean image
) {
}
