package com.prelude.resume.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One turn as the workspace stream renders it.
 *
 * <p>{@code failureReason} is additive: a turn that ended in failure has no assistant message,
 * and without a reason the workspace could only show it as an instruction nobody answered.
 */
public record ResumeTurnResponse(
    Long id,
    String instruction,
    String status,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String failureReason,
    List<ResumeAssistantMessageResponse> messages
) {
}
