package com.interview.dto;

import java.time.LocalDateTime;

public record InterviewStageUpdateResponse(
    String stageName,
    LocalDateTime startedAt
) {
}
