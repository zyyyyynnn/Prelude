package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTypes;
import com.interview.platform.job.ReportJobChannel;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class PendingJobRecoveryPublisher {

    private final AsyncJobMapper asyncJobMapper;
    private final RabbitTemplate rabbitTemplate;
    private final long redispatchAfterSeconds;
    private final long executionLeaseSeconds;

    public PendingJobRecoveryPublisher(
        AsyncJobMapper asyncJobMapper,
        RabbitTemplate rabbitTemplate,
        @Value("${prelude.jobs.redispatch-after-seconds:60}") long redispatchAfterSeconds,
        @Value("${prelude.jobs.execution-lease-seconds:300}") long executionLeaseSeconds
    ) {
        this.asyncJobMapper = asyncJobMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.redispatchAfterSeconds = Math.max(1, redispatchAfterSeconds);
        this.executionLeaseSeconds = Math.max(1, executionLeaseSeconds);
    }

    @Scheduled(fixedDelayString = "${prelude.jobs.dispatch-recovery-delay-ms:30000}")
    public void recoverPendingJobs() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(redispatchAfterSeconds);
        LocalDateTime leaseExpiredBefore = LocalDateTime.now().minusSeconds(executionLeaseSeconds);
        List<AsyncJob> pending = asyncJobMapper.selectList(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getType, JobTypes.REPORT_GENERATE)
            .and(status -> status
                .and(pendingStatus -> pendingStatus
                    .eq(AsyncJob::getStatus, JobStatuses.PENDING)
                    .and(dispatch -> dispatch
                        .isNull(AsyncJob::getDispatchedAt)
                        .or()
                        .lt(AsyncJob::getDispatchedAt, staleBefore)))
                .or(runningStatus -> runningStatus
                    .eq(AsyncJob::getStatus, JobStatuses.RUNNING)
                    .and(started -> started
                        .isNull(AsyncJob::getStartedAt)
                        .or()
                        .lt(AsyncJob::getStartedAt, leaseExpiredBefore))))
            .orderByAsc(AsyncJob::getCreatedAt)
            .last("LIMIT 100"));

        for (AsyncJob job : pending) {
            try {
                rabbitTemplate.convertAndSend(
                    ReportJobChannel.EXCHANGE,
                    ReportJobChannel.ROUTING_KEY,
                    new ReportJobMessage(job.getSubjectId(), job.getUserId(), job.getJobId())
                );
                job.setDispatchedAt(LocalDateTime.now());
                asyncJobMapper.updateById(job);
                log.info("Redispatched recoverable job {} status={}", job.getJobId(), job.getStatus());
            } catch (RuntimeException error) {
                log.warn("Failed to redispatch job {} (type={}): {}",
                    job.getJobId(), error.getClass().getSimpleName(), JobFailureMessage.sanitize(error));
            }
        }
    }
}
