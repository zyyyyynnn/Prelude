package com.interview.insight.application;

import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.JobExecutionPort;
import com.interview.platform.job.JobExecutionPort.ClaimResult;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ReportGenerateHandlerTest {

    private final GenerateInterviewReport useCase = mock(GenerateInterviewReport.class);
    private final JobExecutionPort store = mock(JobExecutionPort.class);
    private final ReportJobMessage message = new ReportJobMessage(7L, 42L, "job-1");

    @Test
    void marksSuccessfulExecutionAsSucceeded() {
        when(store.claimAttempt(message, 3)).thenReturn(ClaimResult.STARTED);
        when(useCase.execute(7L, 42L)).thenReturn(GenerateInterviewReport.Outcome.COMPLETED);

        new ReportGenerateHandler(useCase, store, 3).handle(message);

        verify(store).markSucceeded("job-1");
        verify(store, never()).markFailed("job-1", null);
    }

    @Test
    void retriesUpToConfiguredLimitBeforeSucceeding() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        when(store.claimAttempt(message, 3)).thenReturn(ClaimResult.STARTED);
        when(useCase.execute(7L, 42L))
            .thenThrow(first)
            .thenThrow(second)
            .thenReturn(GenerateInterviewReport.Outcome.COMPLETED);

        new ReportGenerateHandler(useCase, store, 3).handle(message);

        verify(store).markRetry("job-1", first);
        verify(store).markRetry("job-1", second);
        verify(store).markSucceeded("job-1");
        verify(useCase, times(3)).execute(7L, 42L);
    }

    @Test
    void recordsTerminalFailureAndRestoresSessionAfterLimit() {
        RuntimeException failure = new RuntimeException("down");
        when(store.claimAttempt(message, 2)).thenReturn(ClaimResult.STARTED);
        when(useCase.execute(7L, 42L)).thenThrow(failure);

        new ReportGenerateHandler(useCase, store, 2).handle(message);

        verify(store).markRetry("job-1", failure);
        verify(store).markFailed("job-1", failure);
        verify(useCase).handleTerminalFailure(7L, failure);
    }

    @Test
    void ignoresDuplicateOrTerminalDelivery() {
        when(store.claimAttempt(message, 3)).thenReturn(ClaimResult.TERMINAL_OR_DUPLICATE);

        new ReportGenerateHandler(useCase, store, 3).handle(message);

        verify(useCase, never()).execute(7L, 42L);
    }

    @Test
    void finalizesAStaleJobWhoseAttemptBudgetIsExhausted() {
        when(store.claimAttempt(message, 3)).thenReturn(ClaimResult.EXHAUSTED);

        new ReportGenerateHandler(useCase, store, 3).handle(message);

        verify(store).markFailed(org.mockito.ArgumentMatchers.eq("job-1"), org.mockito.ArgumentMatchers.any(RuntimeException.class));
        verify(useCase).handleTerminalFailure(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(RuntimeException.class));
        verify(useCase, never()).execute(7L, 42L);
    }
}
