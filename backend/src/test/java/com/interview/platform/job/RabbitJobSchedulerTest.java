package com.interview.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.bootstrap.RabbitMqConfig;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.AsyncJobMapper;
import com.interview.platform.job.infrastructure.RabbitJobScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitJobSchedulerTest {

    private final AsyncJobMapper mapper = mock(AsyncJobMapper.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitJobScheduler scheduler = new RabbitJobScheduler(mapper, rabbitTemplate);

    @Test
    void persistsPendingJobBeforePublishing() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        JobTicket ticket = scheduler.enqueue(JobRequest.report(7L, 42L));

        ArgumentCaptor<AsyncJob> row = ArgumentCaptor.forClass(AsyncJob.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(JobStatuses.PENDING);
        assertThat(row.getValue().getIdempotencyKey()).isEqualTo("report.generate:session:7");
        verify(rabbitTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq(RabbitMqConfig.REPORT_EXCHANGE),
            org.mockito.ArgumentMatchers.eq(RabbitMqConfig.REPORT_ROUTING_KEY),
            any(ReportJobMessage.class)
        );
        assertThat(ticket.jobId()).isNotBlank();
    }

    @Test
    void returnsExistingPendingJobWithoutRepublishing() {
        AsyncJob existing = new AsyncJob();
        existing.setJobId("job-existing");
        existing.setStatus(JobStatuses.PENDING);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        JobTicket ticket = scheduler.enqueue(JobRequest.report(7L, 42L));

        assertThat(ticket).isEqualTo(new JobTicket("job-existing", JobStatuses.PENDING));
        verify(mapper, never()).insert(any(AsyncJob.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
