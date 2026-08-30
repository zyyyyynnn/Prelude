package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.jobs.JobRequest;
import com.prelude.jobs.JobSchedulerPort;
import com.prelude.jobs.JobTicket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInterviewTest {

    @Test
    void schedulesAReportOnlyForTheOwnedClosingSession() {
        InterviewSessionAccess access = mock(InterviewSessionAccess.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        JobSchedulerPort jobs = mock(JobSchedulerPort.class);
        InterviewMessageService messages = mock(InterviewMessageService.class);
        InterviewStageManager stages = mock(InterviewStageManager.class);
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setStatus("ongoing");
        when(access.currentAccountId()).thenReturn(7L);
        when(access.requireOwned(51L, 7L)).thenReturn(session);
        when(stages.currentStageName(51L)).thenReturn("closing");
        when(jobs.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(new JobTicket("job-1", "pending"));

        FinishInterviewResult result = new FinishInterview(access, sessions, jobs, messages, stages).execute(51L);

        assertThat(result.status()).isEqualTo("generating");
        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(session.getStatus()).isEqualTo("generating");
        verify(access).requireOwned(51L, 7L);
        verify(sessions).update(session);
        ArgumentCaptor<JobRequest> request = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobs).enqueue(request.capture());
        assertThat(request.getValue().accountId()).isEqualTo(7L);
        assertThat(request.getValue().subjectId()).isEqualTo(51L);
        verify(messages).invalidateSessionLock(51L);
    }
}
