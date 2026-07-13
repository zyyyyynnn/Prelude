package com.interview.interview.api;

import java.time.LocalDateTime;

public record InterviewMessageItemResponse(
    Long id,
    String role,
    String content,
    Integer seqNum,
    LocalDateTime createdAt,
    Integer score,
    String hint
) {
}
