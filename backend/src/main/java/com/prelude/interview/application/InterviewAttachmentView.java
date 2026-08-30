package com.prelude.interview.application;

public record InterviewAttachmentView(
    Long id,
    String fileName,
    String mediaType,
    long size,
    boolean image
) {
}
