package com.interview.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.AsyncJobMapper;
import com.interview.platform.job.infrastructure.PendingJobRecoveryPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingJobRecoveryPublisherTest {

    @Test
    void redispatchesStalePendingJobsAndRecordsDispatchTime() {
        AsyncJobMapper mapper = mock(AsyncJobMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AsyncJob job = new AsyncJob();
        job.setJobId("job-1");
        job.setUserId(42L);
        job.setSubjectId(7L);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));

        new PendingJobRecoveryPublisher(mapper, rabbitTemplate, 60).recoverPendingJobs();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ReportJobMessage.class));
        verify(mapper).updateById(job);
        assertThat(job.getDispatchedAt()).isNotNull();
    }
}
