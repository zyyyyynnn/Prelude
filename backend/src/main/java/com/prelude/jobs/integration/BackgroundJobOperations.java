package com.prelude.jobs.integration;

/**
 * Cross-module contract for durable background work. Interview requests
 * jobs; artifact (and future consumers) execute through this boundary — no
 * other module touches background_job/job_attempt tables, RabbitTemplate or
 * jobs infrastructure.
 */
public interface BackgroundJobOperations {

    BackgroundJobRef request(BackgroundJobRequest request);

    ClaimOutcome claim(String jobId);

    void complete(String jobId);

    void fail(String jobId, String sanitizedFailure);

    boolean cancel(String jobId, Long accountId);

    BackgroundJobView view(String jobId, Long accountId);

    /**
     * Authoritative job projection for a dispatched worker: the broker message
     * carries only the jobId, and the worker re-reads subject/account through
     * this boundary before executing.
     */
    BackgroundJobView dispatchedJob(String jobId);

    record BackgroundJobRequest(
        String type,
        Long accountId,
        Long subjectId,
        String operationKey,
        String payloadJson
    ) {
    }

    record BackgroundJobRef(String jobId, String status) {
    }

    /**
     * Result of an atomic PENDING → RUNNING claim.
     */
    record ClaimOutcome(boolean claimed, String status, String reason) {

        public static ClaimOutcome start() {
            return new ClaimOutcome(true, "RUNNING", "claimed");
        }

        public static ClaimOutcome skip(String status, String reason) {
            return new ClaimOutcome(false, status, reason);
        }
    }

    record BackgroundJobView(
        String jobId,
        String type,
        Long subjectId,
        Long accountId,
        String status,
        int attemptCount,
        int maxAttempts,
        String lastError
    ) {
    }
}
