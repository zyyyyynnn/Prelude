package com.prelude.interview.application.port;

import java.time.LocalDateTime;

/**
 * The user turn another module needs to react to. Carries only the content and
 * identity of the stored message, so a consumer never names an interview domain class.
 */
public record InterviewUserTurnSnapshot(
    Long messageId,
    Long sessionId,
    String content,
    Integer seqNum,
    LocalDateTime createdAt
) {
}
