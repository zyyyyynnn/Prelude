package com.interview.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.job.infrastructure.RabbitJobScheduler;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
            org.mockito.ArgumentMatchers.eq(ReportJobChannel.EXCHANGE),
            org.mockito.ArgumentMatchers.eq(ReportJobChannel.ROUTING_KEY),
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

    @Test
    void sanitizesPublisherFailureBeforePersistingIt() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doThrow(new IllegalStateException(
            "Authorization: Bearer sk-super-secret https://user:pass@example.com/v1?api_key=hidden"
        )).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ReportJobMessage.class));

        assertThatThrownBy(() -> scheduler.enqueue(JobRequest.report(7L, 42L)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("报告生成任务发布失败");

        ArgumentCaptor<AsyncJob> failed = ArgumentCaptor.forClass(AsyncJob.class);
        verify(mapper).updateById(failed.capture());
        assertThat(failed.getValue().getLastError())
            .contains("[REDACTED]")
            .doesNotContain("super-secret", "user:pass", "api_key=hidden");
    }
}
