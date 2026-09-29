package com.prelude.resume.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What a run wants to change, held apart from the document until someone decides.
 *
 * <p>{@code baseRevision} is the whole point of keeping this row: the moment the resume
 * moves past it, the proposal is {@link Status#STALE} and its operations are never applied.
 * Nothing here rewrites a patch onto a newer document — a stale proposal is a dead end and
 * the candidate asks again.
 */
public record ResumePatchProposal(
    Long id,
    Long runId,
    Long resumeId,
    Long conversationId,
    int baseRevision,
    List<String> affectedBlockIds,
    String reason,
    List<BlockOperation> operations,
    List<FactRisk> factRisk,
    Status status,
    String decisionNote,
    LocalDateTime decidedAt,
    Long decidedByAccountId,
    LocalDateTime createdAt
) {

    public ResumePatchProposal {
        affectedBlockIds = affectedBlockIds == null ? List.of() : List.copyOf(affectedBlockIds);
        operations = operations == null ? List.of() : List.copyOf(operations);
        factRisk = factRisk == null ? List.of() : List.copyOf(factRisk);
    }

    public enum Status {
        PENDING("pending"),
        ACCEPTED("accepted"),
        REJECTED("rejected"),
        STALE("stale"),
        INVALID("invalid");

        private final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Status fromWire(String value) {
            for (Status candidate : values()) {
                if (candidate.wire.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("未知的简历提案状态: " + value);
        }
    }

    /**
     * A claim the patch introduces that the material the candidate supplied does not
     * support. It is shown, not silently dropped: a reviewer can accept a number they know
     * is real, and the workspace must not hide which one that is.
     */
    public record FactRisk(String blockId, String statement) {
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
