package com.prelude.resume.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A patch awaiting or holding a decision.
 *
 * <p>{@code stale} is decided by comparing {@code baseRevision} with {@code latestRevision}
 * on the way out: the proposal never rewrites itself onto a newer document, and the
 * workspace has to be able to say why the accept button is gone.
 */
public record ResumeProposalResponse(
    Long id,
    Long runId,
    Long resumeId,
    int baseRevision,
    int latestRevision,
    boolean decidable,
    List<String> affectedBlockIds,
    String reason,
    List<Change> changes,
    List<FactRisk> factRisk,
    String status,
    String decisionNote,
    LocalDateTime createdAt,
    LocalDateTime decidedAt,
    Long resultingRevisionId
) {

    /** One block as it stands now and as the patch would make it. */
    public record Change(String kind, String blockId, String section, String before, String after) {
    }

    /** A claim the candidate's own material does not support. */
    public record FactRisk(String blockId, String statement) {
    }
}
