package com.interview.interview.application;

import com.interview.interview.application.InterviewContextService;
import com.interview.interview.application.InterviewJudgeService;
import com.interview.interview.application.InterviewMessageService;
import com.interview.interview.application.InterviewStageManager;
import com.interview.interview.application.InterviewSummaryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.realtime.RealtimePort;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.InterviewSessionAccess;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
import com.interview.platform.llm.LlmRouter;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.interview.infrastructure.persistence.InterviewStageMapper;
import com.interview.catalog.infrastructure.persistence.PositionTemplateMapper;
import com.interview.resume.infrastructure.persistence.ResumeMapper;
import com.interview.insight.infrastructure.persistence.ScoreHistoryMapper;
import com.interview.insight.infrastructure.persistence.UserWeaknessMapper;
import com.interview.insight.infrastructure.InterviewReportParser;
import com.interview.platform.job.JobRequest;
import com.interview.platform.job.JobSchedulerPort;
import com.interview.platform.job.JobTicket;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinishInterviewTest {

    @Mock private StartInterview startInterview;
    @Mock private PositionTemplateMapper positionTemplateMapper;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private InterviewStageMapper interviewStageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper userWeaknessMapper;
    @Mock private LlmRouter llmRouter;
    @Mock private InterviewFixturePort devFixtureService;
    @Mock private InterviewReportParser interviewReportParser;
    @Mock private RealtimePort sseEmitterRegistry;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private JobSchedulerPort jobSchedulerPort;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private InterviewContextService interviewContextService;
    @Mock private InterviewJudgeService interviewJudgeService;
    @Mock private InterviewSummaryService interviewSummaryService;
    private final Executor directExecutor = Runnable::run;

    private FinishInterview finishInterview;

    @BeforeEach
    void setUp() {
        InterviewSessionAccess sessionAccess = new InterviewSessionAccess(interviewSessionMapper);
        InterviewMessageService messageService = new InterviewMessageService(interviewMessageMapper);
        finishInterview = new FinishInterview(
            sessionAccess,
            interviewSessionMapper,
            jobSchedulerPort,
            messageService
        );
        lenient().when(jobSchedulerPort.enqueue(any(JobRequest.class))).thenReturn(new JobTicket("job-1", "pending"));
        UserContext.setCurrentUserId(42L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void finishPublishesJobToRabbitAndReturnsGenerating() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        FinishInterviewResult response = finishInterview.execute(7L);

        assertThat(response.sessionId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("generating");
        assertThat(response.jobId()).isNotBlank();
        assertThat(response.summaryReport()).isNull();

        ArgumentCaptor<JobRequest> jobCaptor = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobSchedulerPort).enqueue(jobCaptor.capture());
        JobRequest published = jobCaptor.getValue();
        assertThat(published.subjectId()).isEqualTo(7L);
        assertThat(published.userId()).isEqualTo(42L);
        assertThat(response.jobId()).isEqualTo("job-1");

        verify(interviewSessionMapper, times(1)).update(any(InterviewSession.class));
    }

    @Test
    void finishRestoresSessionToOngoingWhenRabbitPublishFails() {
        InterviewSession ongoing = new InterviewSession();
        ongoing.setId(7L);
        ongoing.setUserId(42L);
        ongoing.setStatus("ongoing");

        InterviewSession generating = new InterviewSession();
        generating.setId(7L);
        generating.setUserId(42L);
        generating.setStatus("generating");

        when(interviewSessionMapper.selectById(7L))
            .thenReturn(ongoing)
            .thenReturn(generating);
        doThrow(new RuntimeException("broker down"))
            .when(jobSchedulerPort).enqueue(any(JobRequest.class));

        assertThatThrownBy(() -> finishInterview.execute(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("报告生成任务发布失败");

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(2)).update(sessionCaptor.capture());
        InterviewSession restored = sessionCaptor.getAllValues().get(1);
        assertThat(restored.getStatus()).isEqualTo("ongoing");
    }

    @Test
    void finishLeavesGeneratingStateIntactWhenRabbitPublishSucceeds() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        finishInterview.execute(7L);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).update(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo("generating");
    }

    @Test
    void finishDoesNotTouchSseEmitterRegistry() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        finishInterview.execute(7L);

        verify(sseEmitterRegistry, never()).publish(any(), anyString(), anyString());
    }

    @Test
    void finishReturnsGeneratingWithoutRepublishWhenAlreadyGenerating() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("generating");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        FinishInterviewResult response = finishInterview.execute(7L);

        assertThat(response.status()).isEqualTo("generating");
        assertThat(response.jobId()).isNull();
        assertThat(response.summaryReport()).isNull();
        verify(jobSchedulerPort, never()).enqueue(any(JobRequest.class));
        verify(interviewSessionMapper, never()).update(any(InterviewSession.class));
    }

    @Test
    void finishReturnsFinishedReportWithoutRepublishWhenAlreadyFinished() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("finished");
        session.setSummaryReport("# Report");
        when(interviewSessionMapper.selectById(7L)).thenReturn(session);

        FinishInterviewResult response = finishInterview.execute(7L);

        assertThat(response.status()).isEqualTo("finished");
        assertThat(response.summaryReport()).isEqualTo("# Report");
        assertThat(response.jobId()).isNull();
        verify(jobSchedulerPort, never()).enqueue(any(JobRequest.class));
        verify(interviewSessionMapper, never()).update(any(InterviewSession.class));
    }
}
