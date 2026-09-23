package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.jobs.integration.BackgroundJobOperations;
import org.junit.jupiter.api.Test;

/**
 * Finish is a fenced ongoing-to-generating transition: a second finisher that lost the
 * CAS reports GENERATING and never schedules a second report job.
 */
class FinishInterviewStatusRaceTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final BackgroundJobOperations jobs = mock(BackgroundJobOperations.class);
    private final InterviewStageManager stages = mock(InterviewStageManager.class);
    private final FinishInterview finishInterview =
        new FinishInterview(sessionAccess, sessions, jobs, stages);

    private InterviewSession session(String status) {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setStatus(status);
        return session;
    }

    @Test
    void theWinnerOfTheTransitionSchedulesExactlyOneReportJob() {
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        when(sessionAccess.requireOwned(51L, 7L)).thenReturn(session("ongoing"));
        when(stages.currentStageName(51L)).thenReturn("closing");
        when(sessions.markGeneratingIfOngoing(51L, 7L)).thenReturn(1);
        when(jobs.request(any())).thenReturn(new BackgroundJobOperations.BackgroundJobRef("job-1", "op-1"));

        FinishInterviewResult result = finishInterview.execute(51L);

        assertThat(result.status()).isEqualTo("generating");
        assertThat(result.jobId()).isEqualTo("job-1");
        verify(jobs).request(any());
    }

    @Test
    void aLoserOfTheTransitionReportsGeneratingWithoutASecondJob() {
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        when(sessionAccess.requireOwned(51L, 7L)).thenReturn(session("ongoing"));
        when(stages.currentStageName(51L)).thenReturn("closing");
        when(sessions.markGeneratingIfOngoing(51L, 7L)).thenReturn(0);

        FinishInterviewResult result = finishInterview.execute(51L);

        assertThat(result.status()).isEqualTo("generating");
        assertThat(result.jobId()).isNull();
        verify(jobs, never()).request(any());
    }

    @Test
    void anAlreadyGeneratingSessionIsReturnedAsIs() {
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        when(sessionAccess.requireOwned(51L, 7L)).thenReturn(session("generating"));

        FinishInterviewResult result = finishInterview.execute(51L);

        assertThat(result.status()).isEqualTo("generating");
        verify(sessions, never()).markGeneratingIfOngoing(anyLong(), anyLong());
        verify(jobs, never()).request(any());
    }

    @Test
    void aFinishedSessionReturnsItsReport() {
        InterviewSession finished = session("finished");
        finished.setSummaryReport("{\"ok\":true}");
        when(sessionAccess.currentAccountId()).thenReturn(7L);
        when(sessionAccess.requireOwned(51L, 7L)).thenReturn(finished);

        FinishInterviewResult result = finishInterview.execute(51L);

        assertThat(result.status()).isEqualTo("finished");
        assertThat(result.summaryReport()).isEqualTo("{\"ok\":true}");
    }
}
