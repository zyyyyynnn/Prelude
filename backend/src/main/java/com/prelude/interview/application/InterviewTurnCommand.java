package com.prelude.interview.application;

public record InterviewTurnCommand(
    Long sessionId,
    Long userId,
    String content,
    boolean autoStart,
    boolean completionPrompt
) {
}
