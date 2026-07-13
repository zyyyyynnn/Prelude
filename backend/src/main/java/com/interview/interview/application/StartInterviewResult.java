package com.interview.interview.application;

public record StartInterviewResult(
    Long sessionId,
    String targetPosition,
    String currentStage
) {
}
