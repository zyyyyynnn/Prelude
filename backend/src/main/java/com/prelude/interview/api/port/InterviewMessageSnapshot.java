package com.prelude.interview.api.port;

import java.time.LocalDateTime;

public record InterviewMessageSnapshot(
    Long id,
    Long sessionId,
    String role,
    String content,
    Integer seqNum,
    Integer score,
    String hint,
    String assessmentStatus,
    String assessmentError,
    LocalDateTime createdAt
) {
}
