package com.prelude.resume.domain;

import java.time.LocalDateTime;

/**
 * One immutable state of a resume document.
 *
 * <p>Revisions are never rewritten and never renumbered: an accepted patch appends one,
 * and the number a proposal was built against is what decides whether it is still valid.
 */
public record ResumeRevision(
    Long id,
    Long resumeId,
    int revisionNumber,
    ResumeDocument document,
    String summary,
    Origin origin,
    Long proposalId,
    LocalDateTime createdAt
) {

    /** Where a revision came from — a model never appears here, only a decision does. */
    public enum Origin {
        IMPORTED("imported"),
        USER_EDIT("user_edit"),
        AGENT_PATCH("agent_patch");

        private final String wire;

        Origin(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Origin fromWire(String value) {
            for (Origin candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历版本来源: " + value);
        }
    }
}
