package com.prelude.resume.api;

import java.time.LocalDateTime;
import java.util.List;

/** One turn as the workspace stream renders it. */
public record ResumeTurnResponse(
    Long id,
    String instruction,
    String status,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    List<ResumeAssistantMessageResponse> messages
) {
}
