package com.interview.interview.api;

public record InterviewStartResponse(
    Long sessionId,
    String targetPosition,
    String currentStage
) {
}
