package com.prelude.jobs.integration;

/** Published exactly once when a durable PENDING -> CANCELLED transition wins. */
public record BackgroundJobCancelled(
    String jobId,
    String type,
    Long accountId,
    Long subjectId
) {
}
