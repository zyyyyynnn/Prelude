package com.interview.platform.job.api;

import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTypes;
import com.interview.platform.job.api.JobController;
import com.interview.platform.job.api.JobQueryService;
import com.interview.platform.job.api.JobStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerWebMvcTest {

    @Test
    void returnsQueryableJobStatusContract() throws Exception {
        JobQueryService queryService = mock(JobQueryService.class);
        when(queryService.requireOwned("job-1")).thenReturn(new JobStatusResponse(
            "job-1", JobTypes.REPORT_GENERATE, 7L, JobStatuses.RUNNING, 1, null, null
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new JobController(queryService)).build();

        mockMvc.perform(get("/api/jobs/job-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.jobId").value("job-1"))
            .andExpect(jsonPath("$.data.type").value("report.generate"))
            .andExpect(jsonPath("$.data.status").value("running"))
            .andExpect(jsonPath("$.data.attempts").value(1));
    }
}
