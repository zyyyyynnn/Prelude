package com.interview.interview.application;

import java.time.LocalDateTime;

public record InterviewMessageView(
    Long id,
    String role,
    String content,
    Integer seqNum,
    LocalDateTime createdAt,
    Integer score,
    String hint
) {
}
