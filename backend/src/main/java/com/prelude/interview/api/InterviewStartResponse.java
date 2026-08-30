package com.prelude.interview.api;

public record InterviewStartResponse(
    Long sessionId,
    String targetPosition,
    String currentStage
) {
}
