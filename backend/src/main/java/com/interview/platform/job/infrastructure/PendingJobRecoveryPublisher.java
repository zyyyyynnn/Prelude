package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.bootstrap.RabbitMqConfig;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.AsyncJob;
import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTypes;
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

    public PendingJobRecoveryPublisher(
        AsyncJobMapper asyncJobMapper,
        RabbitTemplate rabbitTemplate,
        @Value("${prelude.jobs.redispatch-after-seconds:60}") long redispatchAfterSeconds
    ) {
        this.asyncJobMapper = asyncJobMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.redispatchAfterSeconds = Math.max(1, redispatchAfterSeconds);
    }

    @Scheduled(fixedDelayString = "${prelude.jobs.dispatch-recovery-delay-ms:30000}")
    public void recoverPendingJobs() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(redispatchAfterSeconds);
        List<AsyncJob> pending = asyncJobMapper.selectList(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getType, JobTypes.REPORT_GENERATE)
            .eq(AsyncJob::getStatus, JobStatuses.PENDING)
            .and(dispatch -> dispatch
                .isNull(AsyncJob::getDispatchedAt)
                .or()
                .lt(AsyncJob::getDispatchedAt, staleBefore))
            .orderByAsc(AsyncJob::getCreatedAt)
            .last("LIMIT 100"));

        for (AsyncJob job : pending) {
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMqConfig.REPORT_EXCHANGE,
                    RabbitMqConfig.REPORT_ROUTING_KEY,
                    new ReportJobMessage(job.getSubjectId(), job.getUserId(), job.getJobId())
                );
                job.setDispatchedAt(LocalDateTime.now());
                asyncJobMapper.updateById(job);
                log.info("Redispatched pending job {}", job.getJobId());
            } catch (RuntimeException error) {
                log.warn("Failed to redispatch pending job {}: {}", job.getJobId(), error.getMessage());
            }
        }
    }
}
