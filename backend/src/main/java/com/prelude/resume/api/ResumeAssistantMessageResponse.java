package com.prelude.resume.api;

import java.time.LocalDateTime;

/** An assistant message and the run trace that produced it. */
public record ResumeAssistantMessageResponse(
    Long id,
    Long turnId,
    String content,
    LocalDateTime createdAt,
    ResumeToolGroupResponse toolCalls
) {
}
