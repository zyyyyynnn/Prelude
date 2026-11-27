package com.prelude.jobs.integration;

/** Reads account-owned background job state. */
public interface JobQuery {

    BackgroundJobMessages.BackgroundJobView view(String jobId, Long accountId);
}
