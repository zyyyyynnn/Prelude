package com.prelude.jobs.integration;

/**
 * Published exactly once when a durable job reaches terminal FAILED.
 */
public record BackgroundJobFailed(
    String jobId,
    String type,
    Long accountId,
    Long subjectId,
    String failureSummary
) {
}
