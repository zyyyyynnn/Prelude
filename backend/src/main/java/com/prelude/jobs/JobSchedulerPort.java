package com.prelude.jobs;

public interface JobSchedulerPort {

    JobTicket enqueue(JobRequest request);
}
