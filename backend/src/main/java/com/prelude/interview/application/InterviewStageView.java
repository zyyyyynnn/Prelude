package com.prelude.interview.application;

import java.time.LocalDateTime;

public record InterviewStageView(
    String stageName,
    LocalDateTime startedAt,
    LocalDateTime endedAt
) {
}
