package com.prelude.interview.application.port;

import com.prelude.BusinessException;

public record InterviewTurnCommand(
    Long sessionId,
    Long accountId,
    String content,
    boolean autoStart,
    Long retryOfMessageId,
    Long supersedesMessageId
) {
    public InterviewTurnCommand {
        if (retryOfMessageId != null && supersedesMessageId != null) {
            throw BusinessException.badRequest("重试与取代回答不能同时指定");
        }
        if (autoStart && (retryOfMessageId != null || supersedesMessageId != null)) {
            throw BusinessException.badRequest("自动开始不能与重试或取代回答同时指定");
        }
        if (autoStart && content != null && !content.isBlank()) {
            throw BusinessException.badRequest("自动开始不能携带回答内容");
        }
    }

    public InterviewTurnCommand(Long sessionId, Long accountId, String content, boolean autoStart) {
        this(sessionId, accountId, content, autoStart, null, null);
    }
}
