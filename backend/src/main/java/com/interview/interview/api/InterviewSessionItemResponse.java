package com.interview.interview.api;

import java.time.LocalDateTime;

public record InterviewSessionItemResponse(
    Long sessionId,
    String targetPosition,
    String status,
    LocalDateTime createdAt,
    String currentStage,
    String llmProvider,
    String llmModel,
    String summaryReport
) {
}
