package com.prelude.jobs.integration;

/** Submits and cancels durable background work. */
public interface JobDispatcher {

    BackgroundJobMessages.BackgroundJobRef request(BackgroundJobMessages.BackgroundJobRequest request);

    BackgroundJobMessages.BackgroundJobView cancel(String jobId, Long accountId);
}
