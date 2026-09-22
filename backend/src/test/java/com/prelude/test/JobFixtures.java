package com.prelude.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import com.prelude.jobs.infrastructure.persistence.BackgroundJob;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.jobs.infrastructure.persistence.JobAttempt;
import com.prelude.jobs.infrastructure.persistence.JobAttemptMapper;

import java.util.List;

public final class JobFixtures {

    private JobFixtures() {
    }

    public static BackgroundJobSucceeded succeeded(String jobId, String jobType, long accountId, Long targetId) {
        return new BackgroundJobSucceeded(jobId, jobType, accountId, targetId);
    }

    public static BackgroundJobFailed failed(String jobId, String jobType, long accountId, Long targetId, String errorMessage) {
        return new BackgroundJobFailed(jobId, jobType, accountId, targetId, errorMessage);
    }

    public static BackgroundJobCancelled cancelled(String jobId, String jobType, long accountId, Long targetId) {
        return new BackgroundJobCancelled(jobId, jobType, accountId, targetId);
    }

    public static Class<BackgroundJobSucceeded> succeededEventClass() {
        return BackgroundJobSucceeded.class;
    }

    public static Class<BackgroundJobFailed> failedEventClass() {
        return BackgroundJobFailed.class;
    }

    public static Class<BackgroundJobCancelled> cancelledEventClass() {
        return BackgroundJobCancelled.class;
    }

    public static String statusSucceeded() {
        return BackgroundJob.SUCCEEDED;
    }

    public static String statusFailed() {
        return BackgroundJob.FAILED;
    }

    public static String statusCancelled() {
        return BackgroundJob.CANCELLED;
    }

    public static String statusRunning() {
        return BackgroundJob.RUNNING;
    }

    public static String statusPending() {
        return BackgroundJob.PENDING;
    }

    public static String statusInterrupted() {
        return JobAttempt.INTERRUPTED;
    }

    public static String attemptStatus(Object attempt) {
        return ((JobAttempt) attempt).getStatus();
    }

    public static List<String> attemptStatuses(JobAttemptMapper mapper, String jobId) {
        return attempts(mapper, jobId).stream().map(JobAttempt::getStatus).toList();
    }

    public static BackgroundJob stored(BackgroundJobMapper mapper, String jobId) {
        return mapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    public static List<JobAttempt> attempts(JobAttemptMapper mapper, String jobId) {
        return mapper.selectList(new LambdaQueryWrapper<JobAttempt>()
            .eq(JobAttempt::getJobId, jobId)
            .orderByAsc(JobAttempt::getAttemptNumber));
    }

    public static long countByOperationKey(BackgroundJobMapper mapper, String operationKey) {
        return mapper.selectCount(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getOperationKey, operationKey));
    }
}