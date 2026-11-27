package com.prelude.interview.application.port;

public record InterviewTurnCommand(
    Long sessionId,
    Long accountId,
    String content,
    boolean autoStart,
    Long retryOfMessageId,
    Long supersedesMessageId
) {
    public InterviewTurnCommand(Long sessionId, Long accountId, String content, boolean autoStart) {
        this(sessionId, accountId, content, autoStart, null, null);
    }
}
