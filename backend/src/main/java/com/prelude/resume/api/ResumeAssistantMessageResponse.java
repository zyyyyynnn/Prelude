package com.prelude.resume.api;

import java.time.LocalDateTime;

public record ResumeAssistantMessageResponse(
    Long id,
    Long turnId,
    String content,
    LocalDateTime createdAt,
    ResumeToolGroupResponse toolCalls
) {
}
