package com.interview.interview.api;

import java.time.LocalDateTime;

public record InterviewStageUpdateResponse(
    String stageName,
    LocalDateTime startedAt
) {
}
