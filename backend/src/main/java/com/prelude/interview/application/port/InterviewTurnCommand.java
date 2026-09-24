package com.prelude.interview.application.port;

public record InterviewTurnCommand(
    Long sessionId,
    Long accountId,
    String content,
    boolean autoStart,
    boolean completionPrompt
) {
}
