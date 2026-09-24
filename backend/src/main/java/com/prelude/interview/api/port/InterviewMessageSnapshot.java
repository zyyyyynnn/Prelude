package com.prelude.interview.api.port;

import java.time.LocalDateTime;

/** A stored interview turn as another module sees it. */
public record InterviewMessageSnapshot(
    Long id,
    Long sessionId,
    String role,
    String content,
    Integer seqNum,
    Integer score,
    String hint,
    LocalDateTime createdAt
) {
}
