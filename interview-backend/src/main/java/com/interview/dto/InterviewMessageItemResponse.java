package com.interview.dto;

import java.time.LocalDateTime;

public record InterviewMessageItemResponse(
    Long id,
    String role,
    String content,
    Integer seqNum,
    LocalDateTime createdAt
) {
}
