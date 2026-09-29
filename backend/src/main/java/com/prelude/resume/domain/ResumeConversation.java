package com.prelude.resume.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One resume work session in the workspace. It hangs off a resume when the candidate
 * knows which document the work is about, and stays unattached until then.
 */
public record ResumeConversation(
    Long id,
    Long accountId,
    Long resumeId,
    String title,
    LocalDateTime pinnedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public ResumeConversation {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(title, "title");
    }

    public static ResumeConversation create(
        Long accountId,
        Long resumeId,
        String title,
        LocalDateTime now
    ) {
        return new ResumeConversation(null, accountId, resumeId, title, null, now, now);
    }

    public boolean pinned() {
        return pinnedAt != null;
    }

    public ResumeConversation withId(Long newId) {
        return new ResumeConversation(
            newId, accountId, resumeId, title, pinnedAt, createdAt, updatedAt);
    }

    public ResumeConversation touchedAt(LocalDateTime moment) {
        return new ResumeConversation(
            id, accountId, resumeId, title, pinnedAt, createdAt, moment);
    }
}
