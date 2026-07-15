package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.platform.job.JobExecutionPort;
import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTypes;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class JobExecutionStore implements JobExecutionPort {

    private final AsyncJobMapper asyncJobMapper;
    private final long leaseSeconds;

    public JobExecutionStore(
        AsyncJobMapper asyncJobMapper,
        @Value("${prelude.jobs.execution-lease-seconds:300}") long leaseSeconds
    ) {
        this.asyncJobMapper = asyncJobMapper;
        this.leaseSeconds = Math.max(1, leaseSeconds);
    }

    public ClaimResult claimAttempt(ReportJobMessage message, int maxAttempts) {
        ensureTracked(message);
        AsyncJob current = find(message.jobId());
        if (current == null || JobStatuses.SUCCEEDED.equals(current.getStatus()) || JobStatuses.FAILED.equals(current.getStatus())) {
            return ClaimResult.TERMINAL_OR_DUPLICATE;
        }
        if (current.getAttempts() != null && current.getAttempts() >= maxAttempts) {
            return ClaimResult.EXHAUSTED;
        }

        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(leaseSeconds);
        int updated = asyncJobMapper.update(null, new UpdateWrapper<AsyncJob>()
            .eq("job_id", message.jobId())
            .lt("attempts", maxAttempts)
            .and(status -> status
                .eq("status", JobStatuses.PENDING)
                .or(stale -> stale
                    .eq("status", JobStatuses.RUNNING)
                    .and(lease -> lease
                        .isNull("started_at")
                        .or()
                        .lt("started_at", staleBefore))))
            .set("status", JobStatuses.RUNNING)
            .set("started_at", LocalDateTime.now())
            .set("last_error", null)
            .setSql("attempts = attempts + 1"));
        return updated == 1 ? ClaimResult.STARTED : ClaimResult.TERMINAL_OR_DUPLICATE;
    }

    public void markRetry(String jobId, Throwable error) {
        update(jobId, JobStatuses.PENDING, error, false);
    }

    public void markSucceeded(String jobId) {
        update(jobId, JobStatuses.SUCCEEDED, null, true);
    }

    public void markFailed(String jobId, Throwable error) {
        update(jobId, JobStatuses.FAILED, error, true);
    }

    private void ensureTracked(ReportJobMessage message) {
        if (find(message.jobId()) != null) {
            return;
        }
        AsyncJob job = new AsyncJob();
        job.setJobId(message.jobId());
        job.setType(JobTypes.REPORT_GENERATE);
        job.setUserId(message.userId());
        job.setSubjectId(message.sessionId());
        job.setIdempotencyKey(JobTypes.REPORT_GENERATE + ":session:" + message.sessionId());
        job.setStatus(JobStatuses.PENDING);
        job.setAttempts(0);
        job.setPayloadJson("{}");
        try {
            asyncJobMapper.insert(job);
        } catch (DuplicateKeyException ignored) {
            // Another consumer or scheduler inserted the same idempotent job first.
        }
    }

    private AsyncJob find(String jobId) {
        return asyncJobMapper.selectOne(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private void update(String jobId, String status, Throwable error, boolean terminal) {
        UpdateWrapper<AsyncJob> update = new UpdateWrapper<AsyncJob>()
            .eq("job_id", jobId)
            .set("status", status)
            .set("last_error", error == null ? null : JobFailureMessage.sanitize(error));
        if (terminal) {
            update.set("finished_at", LocalDateTime.now());
        }
        asyncJobMapper.update(null, update);
    }

}
