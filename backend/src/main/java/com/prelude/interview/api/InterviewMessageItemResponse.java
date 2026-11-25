package com.prelude.interview.api;

import com.prelude.interview.domain.AssessmentStatus;

import java.time.LocalDateTime;

public record InterviewMessageItemResponse(
    Long id,
    String role,
    String content,
    Integer seqNum,
    LocalDateTime createdAt,
    Integer score,
    String hint,
    AssessmentStatus assessmentStatus,
    String assessmentError
) {
}
