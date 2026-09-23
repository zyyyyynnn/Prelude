package com.prelude.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobEntity;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.jobs.infrastructure.persistence.JobAttemptEntity;
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
        return BackgroundJobEntity.SUCCEEDED;
    }

    public static String statusFailed() {
        return BackgroundJobEntity.FAILED;
    }

    public static String statusCancelled() {
        return BackgroundJobEntity.CANCELLED;
    }

    public static String statusRunning() {
        return BackgroundJobEntity.RUNNING;
    }

    public static String statusPending() {
        return BackgroundJobEntity.PENDING;
    }

    public static String statusInterrupted() {
        return JobAttemptEntity.INTERRUPTED;
    }

    public static String attemptStatus(Object attempt) {
        return ((JobAttemptEntity) attempt).getStatus();
    }

    public static List<String> attemptStatuses(JobAttemptMapper mapper, String jobId) {
        return attempts(mapper, jobId).stream().map(JobAttemptEntity::getStatus).toList();
    }

    public static BackgroundJobEntity stored(BackgroundJobMapper mapper, String jobId) {
        return mapper.selectOne(new LambdaQueryWrapper<BackgroundJobEntity>()
            .eq(BackgroundJobEntity::getJobId, jobId)
            .last("LIMIT 1"));
    }

    public static List<JobAttemptEntity> attempts(JobAttemptMapper mapper, String jobId) {
        return mapper.selectList(new LambdaQueryWrapper<JobAttemptEntity>()
            .eq(JobAttemptEntity::getJobId, jobId)
            .orderByAsc(JobAttemptEntity::getAttemptNumber));
    }

    public static long countByOperationKey(BackgroundJobMapper mapper, String operationKey) {
        return mapper.selectCount(new LambdaQueryWrapper<BackgroundJobEntity>()
            .eq(BackgroundJobEntity::getOperationKey, operationKey));
    }
}