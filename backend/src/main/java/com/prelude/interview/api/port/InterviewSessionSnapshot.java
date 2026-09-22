package com.prelude.interview.api.port;

import java.time.LocalDateTime;

/**
 * The session facts another module needs about an interview. Carries only what the
 * report pipeline reads, so the row shape and the interview domain stay inside the
 * interview module.
 */
public record InterviewSessionSnapshot(
    Long id,
    Long accountId,
    String targetPosition,
    Long modelExecutionSnapshotId,
    String status,
    String summary,
    String summaryReport
) {
}
