package com.interview.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.job.api.JobQueryService;
import com.interview.platform.job.api.JobStatusResponse;
import com.interview.platform.job.infrastructure.AsyncJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobQueryServiceTest {

    private final AsyncJobMapper mapper = mock(AsyncJobMapper.class);
    private final JobQueryService queryService = new JobQueryService(mapper);

    @AfterEach
    void clearContext() {
        UserContext.remove();
    }

    @Test
    void returnsOwnedJobWithoutLeakingInternalError() {
        UserContext.setCurrentUserId(42L);
        AsyncJob job = new AsyncJob();
        job.setJobId("job-1");
        job.setType(JobTypes.REPORT_GENERATE);
        job.setUserId(42L);
        job.setSubjectId(7L);
        job.setStatus(JobStatuses.FAILED);
        job.setAttempts(3);
        job.setLastError("secret upstream details");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);

        JobStatusResponse response = queryService.requireOwned("job-1");

        assertThat(response.status()).isEqualTo(JobStatuses.FAILED);
        assertThat(response.attempts()).isEqualTo(3);
        assertThat(response.toString()).doesNotContain("secret upstream details");
    }

    @Test
    void requiresAuthentication() {
        assertThatThrownBy(() -> queryService.requireOwned("job-1"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请先登录");
    }
}
