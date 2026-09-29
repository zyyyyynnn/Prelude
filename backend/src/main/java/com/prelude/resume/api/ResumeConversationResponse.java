package com.prelude.resume.api;

import java.time.LocalDateTime;

/** One conversation row of the resume workspace sidebar. */
public record ResumeConversationResponse(
    Long id,
    String title,
    Long resumeId,
    LocalDateTime updatedAt,
    boolean pinned,
    String status
) {
}
