package com.prelude.interview.application.port;

/**
 * The session facts a cross-module turn consumer needs. Carries identifiers and the
 * model snapshot reference only, so the interview domain stays inside the module.
 */
public record InterviewTurnSessionSnapshot(
    Long sessionId,
    Long accountId,
    String targetPosition,
    Long modelExecutionSnapshotId
) {
}
