package com.interview.platform.job;

public interface JobSchedulerPort {

    JobTicket enqueue(JobRequest request);
}
