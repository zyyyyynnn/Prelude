package com.interview.interview.application;

import com.interview.interview.application.InterviewContextService;
import com.interview.interview.application.InterviewJudgeService;
import com.interview.interview.application.InterviewMessageService;
import com.interview.interview.application.InterviewStageManager;
import com.interview.interview.application.InterviewSummaryService;

import com.interview.shared.web.UserContext;
import com.interview.platform.realtime.RealtimePort;
import com.interview.interview.api.InterviewStartRequest;
import com.interview.interview.api.InterviewStartResponse;
import com.interview.interview.domain.InterviewSession;
import com.interview.catalog.api.port.PositionCatalogPort;
import com.interview.catalog.api.port.PositionCatalogPort.PositionSnapshot;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
import com.interview.platform.llm.LlmSelection;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.platform.retrieval.RetrievalPort;
import com.interview.platform.llm.LlmConfigPort;
import com.interview.platform.llm.PromptVersions;
import com.interview.resume.api.port.ResumeContextPort;
import com.interview.resume.api.port.ResumeProjection;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartInterviewTest {

    @Mock private ResumeContextPort resumeContextPort;
    @Mock private PositionCatalogPort positionCatalogPort;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private LlmConfigPort llmConfigPort;
    @Mock private InterviewFixturePort devFixtureService;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private InterviewContextService interviewContextService;
    @Mock private InterviewJudgeService interviewJudgeService;
    @Mock private InterviewSummaryService interviewSummaryService;
    @Mock private InterviewMessageService interviewMessageService;
    @Mock private RetrievalPort retrievalPort;
    @Mock private RealtimePort sseEmitterRegistry;
    @Mock private RabbitTemplate rabbitTemplate;
    private StartInterview startInterview;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        startInterview = new StartInterview(
            resumeContextPort,
            positionCatalogPort,
            interviewSessionMapper,
            llmConfigPort,
            interviewStageManager,
            interviewMessageService,
            directExecutor,
            retrievalPort
        );
        UserContext.setCurrentUserId(42L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void startPersistsSelectionAndIndexesOwnedResumeProjection() {
        ResumeProjection resume = new ResumeProjection(
            3L, 42L, "resume.pdf", "resume projection", java.util.List.of(), java.util.List.of(), 0
        );
        when(resumeContextPort.requireOwnedProjection(42L, 3L)).thenReturn(resume);

        PositionSnapshot position = new PositionSnapshot(5L, "Java 后端工程师", "system prompt");
        when(positionCatalogPort.findById(5L)).thenReturn(position);
        when(llmConfigPort.resolveSelection(42L, "model-x"))
            .thenReturn(new LlmSelection("provider-x", "model-x"));
        doAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(7L);
            return 1;
        }).when(interviewSessionMapper).add(any(InterviewSession.class));

        InterviewStartRequest request = new InterviewStartRequest();
        request.setResumeId(3L);
        request.setPositionId(5L);
        request.setLlmModel("model-x");
        request.setJdText("job description");

        InterviewStartResponse response = startInterview.execute(request);

        assertThat(response.sessionId()).isEqualTo(7L);
        assertThat(response.targetPosition()).isEqualTo("Java 后端工程师");
        assertThat(response.currentStage()).isEqualTo(InterviewStageManager.STAGE_WARMUP);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper).add(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getLlmProvider()).isEqualTo("provider-x");
        assertThat(sessionCaptor.getValue().getLlmModel()).isEqualTo("model-x");
        assertThat(sessionCaptor.getValue().getPromptVersionsJson())
            .isEqualTo(PromptVersions.DEFAULT_SNAPSHOT_JSON);
        verify(retrievalPort).index(
            RetrievalPort.SCOPE_SESSION,
            7L,
            java.util.List.of("resume projection", "job description")
        );
        verify(interviewMessageService).insertMessage(7L, "system", "system prompt");
        verify(interviewStageManager).ensureInitialStage(eq(sessionCaptor.getValue()));
    }
}
