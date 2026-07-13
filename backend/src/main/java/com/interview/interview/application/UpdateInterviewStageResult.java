package com.interview.interview.application;

import java.time.LocalDateTime;

public record UpdateInterviewStageResult(
    String stageName,
    LocalDateTime startedAt
) {
}
