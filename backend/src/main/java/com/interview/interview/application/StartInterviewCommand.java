package com.interview.interview.application;

public record StartInterviewCommand(
    Long resumeId,
    Long positionId,
    String jdText,
    String llmModel
) {
}
