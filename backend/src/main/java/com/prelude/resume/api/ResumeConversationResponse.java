package com.prelude.resume.api;

import java.time.LocalDateTime;

/** Conversation list row for the sidebar. `status` is derived from the conversation's turns:
 *  {@code active} while a turn is queued or running or the conversation has no turns yet,
 *  {@code finished} once every turn is done. */
public record ResumeConversationResponse(
    Long id,
    String title,
    Long resumeId,
    LocalDateTime updatedAt,
    boolean pinned,
    String status
) {
}
