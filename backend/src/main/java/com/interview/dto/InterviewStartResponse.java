package com.interview.dto;

public record InterviewStartResponse(
    Long sessionId,
    String targetPosition,
    String currentStage
) {
}
