package com.interview.platform.job;

import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.job.api.JobQueryService;
import com.interview.platform.job.api.JobStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobQueryServiceTest {

    private final JobQueryPort queryPort = mock(JobQueryPort.class);
    private final JobQueryService queryService = new JobQueryService(queryPort);

    @AfterEach
    void clearContext() {
        UserContext.remove();
    }

    @Test
    void returnsOwnedJobWithoutLeakingInternalError() {
        UserContext.setCurrentUserId(42L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 10, 0);
        when(queryPort.findOwned("job-1", 42L)).thenReturn(Optional.of(new JobQueryPort.JobSnapshot(
            "job-1",
            JobTypes.REPORT_GENERATE,
            7L,
            JobStatuses.FAILED,
            3,
            createdAt,
            createdAt.plusSeconds(1)
        )));

        JobStatusResponse response = queryService.requireOwned("job-1");

        assertThat(response.status()).isEqualTo(JobStatuses.FAILED);
        assertThat(response.attempts()).isEqualTo(3);
        assertThat(response.subjectId()).isEqualTo(7L);
    }

    @Test
    void requiresAuthentication() {
        assertThatThrownBy(() -> queryService.requireOwned("job-1"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请先登录");
    }

    @Test
    void hidesJobsNotOwnedByCurrentUser() {
        UserContext.setCurrentUserId(42L);
        when(queryPort.findOwned("job-other", 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.requireOwned("job-other"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("任务不存在");
    }
}
