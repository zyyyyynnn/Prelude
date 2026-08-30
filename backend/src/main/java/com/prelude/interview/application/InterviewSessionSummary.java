package com.prelude.interview.application;

import java.time.LocalDateTime;

public record InterviewSessionSummary(
    Long sessionId,
    String targetPosition,
    String status,
    LocalDateTime createdAt,
    String currentStage,
    String llmProvider,
    String llmModel,
    String llmThinkingDepth,
    String summaryReport
) {
}
