package com.prelude.jobs.integration;

/**
 * Published exactly once when the durable RUNNING -> SUCCEEDED transition wins.
 */
public record BackgroundJobSucceeded(
    String jobId,
    String type,
    Long accountId,
    Long subjectId
) {
}
