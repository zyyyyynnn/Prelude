package com.prelude.interview.application;

public record InterviewTurnCommand(
    Long sessionId,
    Long accountId,
    String content,
    boolean autoStart,
    boolean completionPrompt
) {
}
