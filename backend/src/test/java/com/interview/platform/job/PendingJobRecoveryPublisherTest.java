package com.interview.platform.job;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.interview.platform.job.infrastructure.PendingJobRecoveryPublisher;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

        new PendingJobRecoveryPublisher(mapper, rabbitTemplate, 60, 300).recoverPendingJobs();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ReportJobMessage.class));
        verify(mapper).updateById(job);
        assertThat(job.getDispatchedAt()).isNotNull();
    }

    @Test
    void recoveryQueryIncludesExpiredRunningLeases() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "job-recovery-test"),
            AsyncJob.class
        );
        AsyncJobMapper mapper = mock(AsyncJobMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        new PendingJobRecoveryPublisher(mapper, rabbitTemplate, 60, 300).recoverPendingJobs();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AsyncJob>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
            .contains("status")
            .contains("started_at")
            .contains("started_at IS NULL")
            .contains("dispatched_at");
    }
}
