package com.prelude.jobs.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerTest {

    private final BackgroundJobOperations operations = mock(BackgroundJobOperations.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new JobController(operations, currentAccount))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void getReadsTheJobThroughTheAuthenticatedAccount() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(operations.view("job-7", 9L)).thenReturn(
            new BackgroundJobView("job-7", "report.generate", 41L, 9L, "RUNNING", 1, 3, null));

        mockMvc.perform(get("/api/jobs/job-7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobId").value("job-7"))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.attemptCount").value(1))
            .andExpect(jsonPath("$.data.maxAttempts").value(3))
            .andExpect(jsonPath("$.data.lastError").doesNotExist());
    }

    @Test
    void cancelReturnsTheCancelledProjectionAndTheSameOwnershipScope() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(operations.cancel("job-7", 9L)).thenReturn(
            new BackgroundJobView("job-7", "report.generate", 41L, 9L, "CANCELLED", 0, 3, null));

        mockMvc.perform(delete("/api/jobs/job-7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(operations).cancel("job-7", 9L);
    }

    /* A job owned by somebody else answers 404 rather than 403, so polling job ids cannot
       probe which accounts exist. The controller must not soften that. */
    @Test
    void aForeignJobAnswersNotFoundRatherThanForbidden() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(operations.view("job-7", 9L)).thenThrow(BusinessException.notFound("任务不存在"));

        mockMvc.perform(get("/api/jobs/job-7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"))
            .andExpect(jsonPath("$.detail").value("任务不存在"));
    }
}
