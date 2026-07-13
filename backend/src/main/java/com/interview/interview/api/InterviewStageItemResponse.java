package com.interview.interview.api;

import java.time.LocalDateTime;

public record InterviewStageItemResponse(
    String stageName,
    LocalDateTime startedAt,
    LocalDateTime endedAt
) {
}
