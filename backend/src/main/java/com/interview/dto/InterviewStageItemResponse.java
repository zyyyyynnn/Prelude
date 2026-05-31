package com.interview.dto;

import java.time.LocalDateTime;

public record InterviewStageItemResponse(
    String stageName,
    LocalDateTime startedAt,
    LocalDateTime endedAt
) {
}
