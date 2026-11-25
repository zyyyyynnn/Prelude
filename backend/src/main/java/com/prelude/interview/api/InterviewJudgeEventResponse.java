package com.prelude.interview.api;

import com.prelude.interview.domain.AssessmentStatus;

public record InterviewJudgeEventResponse(
    Integer score,
    String hint,
    AssessmentStatus assessmentStatus,
    String error
) {
}
