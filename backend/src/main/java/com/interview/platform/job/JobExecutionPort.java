package com.interview.platform.job;

public interface JobExecutionPort {

    ClaimResult claimAttempt(ReportJobMessage message, int maxAttempts);

    void markRetry(String jobId, Throwable error);

    void markSucceeded(String jobId);

    void markFailed(String jobId, Throwable error);

    enum ClaimResult {
        STARTED,
        EXHAUSTED,
        TERMINAL_OR_DUPLICATE
    }
}
