package com.prelude.jobs.integration;

/** Claims dispatched work and reports its fenced execution outcome. */
public interface JobWorker {

    BackgroundJobMessages.ClaimOutcome claim(String jobId);

    boolean renewLease(String jobId, int attemptNumber);

    BackgroundJobMessages.ExecutionLease keepLeaseAlive(String jobId, int attemptNumber);

    boolean complete(String jobId, int attemptNumber);

    BackgroundJobMessages.FailureOutcome fail(String jobId, int attemptNumber, Throwable failure);

    BackgroundJobMessages.BackgroundJobView dispatchedJob(String jobId);
}
