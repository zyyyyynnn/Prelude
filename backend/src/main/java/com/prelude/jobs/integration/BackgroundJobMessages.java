package com.prelude.jobs.integration;

/** Shared commands, outcomes and projections for the background job ports. */
public interface BackgroundJobMessages {

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

    /** Result of an atomic PENDING → RUNNING claim. */
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
