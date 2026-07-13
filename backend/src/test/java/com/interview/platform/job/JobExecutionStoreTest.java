package com.interview.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.AsyncJobMapper;
import com.interview.platform.job.infrastructure.JobExecutionStore;
import com.interview.platform.job.infrastructure.JobExecutionStore.ClaimResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobExecutionStoreTest {

    private final AsyncJobMapper mapper = mock(AsyncJobMapper.class);
    private final JobExecutionStore store = new JobExecutionStore(mapper, 300);
    private final ReportJobMessage message = new ReportJobMessage(7L, 42L, "job-1");

    @Test
    void atomicallyClaimsPendingAttempt() {
        AsyncJob job = job(JobStatuses.PENDING, 0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        assertThat(store.claimAttempt(message, 3)).isEqualTo(ClaimResult.STARTED);
    }

    @Test
    void refusesAttemptsBeyondConfiguredLimit() {
        AsyncJob job = job(JobStatuses.PENDING, 3);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);

        assertThat(store.claimAttempt(message, 3)).isEqualTo(ClaimResult.EXHAUSTED);
        verify(mapper, never()).update(any(), any(UpdateWrapper.class));
    }

    private AsyncJob job(String status, int attempts) {
        AsyncJob job = new AsyncJob();
        job.setJobId("job-1");
        job.setStatus(status);
        job.setAttempts(attempts);
        return job;
    }
}
