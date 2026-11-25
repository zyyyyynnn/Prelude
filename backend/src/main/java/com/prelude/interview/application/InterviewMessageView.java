package com.prelude.interview.application;

import com.prelude.interview.domain.AssessmentStatus;

import java.time.LocalDateTime;

public record InterviewMessageView(
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
