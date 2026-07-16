package com.interview.resume.domain;

import java.time.LocalDateTime;

public record ResumeImprovement(
    Long id,
    Long userId,
    Long resumeId,
    Long sessionId,
    int ordinal,
    String targetPath,
    String currentText,
    String proposedText,
    String rationale,
    String evidence,
    int baseDocumentVersion,
    String status,
    Integer appliedDocumentVersion,
    LocalDateTime createdAt,
    LocalDateTime decidedAt
) {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
}
