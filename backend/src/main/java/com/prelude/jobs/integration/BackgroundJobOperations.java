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

    boolean renewLease(String jobId, int attemptNumber);

    ExecutionLease keepLeaseAlive(String jobId, int attemptNumber);

    boolean complete(String jobId, int attemptNumber);

    FailureOutcome fail(String jobId, int attemptNumber, Throwable failure);

    BackgroundJobView cancel(String jobId, Long accountId);

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
    record ClaimOutcome(boolean claimed, String status, Integer attemptNumber, String reason) {

        public static ClaimOutcome start(int attemptNumber) {
            return new ClaimOutcome(true, "RUNNING", attemptNumber, "claimed");
        }

        public static ClaimOutcome skip(String status, String reason) {
            return new ClaimOutcome(false, status, null, reason);
        }
    }

    @FunctionalInterface
    interface ExecutionLease extends AutoCloseable {

        @Override
        void close();
    }

    enum FailureOutcome {
        RETRY_SCHEDULED,
        TERMINAL_FAILED,
        NOT_RUNNING
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
