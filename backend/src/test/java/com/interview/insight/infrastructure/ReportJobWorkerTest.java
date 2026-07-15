package com.interview.insight.infrastructure;

import com.interview.insight.infrastructure.ReportJobWorker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.web.UserContext;
import com.interview.platform.realtime.RealtimePort;
import com.interview.resume.api.port.ResumeImprovementPort;
import com.interview.insight.domain.InterviewReportDraft;
import com.interview.insight.domain.StructuredInterviewReport;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;
import com.interview.insight.application.GenerateInterviewReport;
import com.interview.insight.application.ReportGenerateHandler;
import com.interview.insight.domain.InterviewReportAssembler;
import com.interview.insight.infrastructure.InterviewReportParser;
import com.interview.insight.infrastructure.MybatisInsightRepository;
import com.interview.interview.infrastructure.MybatisInterviewReportAdapter;
import com.interview.platform.llm.ChatPort;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.interview.infrastructure.persistence.InterviewStageMapper;
import com.interview.insight.infrastructure.persistence.ScoreHistoryMapper;
import com.interview.insight.infrastructure.persistence.UserWeaknessMapper;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.JobExecutionStore;
import com.interview.platform.job.JobExecutionPort.ClaimResult;
import com.interview.insight.application.port.InsightFixturePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobWorkerTest {

    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private InterviewStageMapper interviewStageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper userWeaknessMapper;
    @Mock private ChatPort chatPort;
    @Mock private InsightFixturePort devFixtureService;
    @Mock private InterviewReportParser interviewReportParser;
    @Mock private InterviewReportAssembler interviewReportAssembler;
    @Mock private RealtimePort sseEmitterRegistry;
    @Mock private JobExecutionStore jobExecutionStore;
    @Mock private ResumeImprovementPort resumeImprovementPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReportJobWorker worker;
    private InterviewReportDraft defaultDraft;
    private StructuredInterviewReport defaultReport;
    private String defaultReportJson;

    @BeforeEach
    void setUp() {
        defaultDraft = draft(7, 6, 8, "# Report");
        defaultReport = report(7, 6, 8, "# Report");
        try {
            defaultReportJson = objectMapper.writeValueAsString(defaultReport);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        GenerateInterviewReport generateInterviewReport = new GenerateInterviewReport(
            objectMapper,
            new MybatisInterviewReportAdapter(interviewSessionMapper, interviewMessageMapper, interviewStageMapper),
            new MybatisInsightRepository(scoreHistoryMapper, userWeaknessMapper),
            chatPort,
            devFixtureService,
            interviewReportParser,
            interviewReportAssembler,
            sseEmitterRegistry,
            resumeImprovementPort
        );
        worker = new ReportJobWorker(new ReportGenerateHandler(generateInterviewReport, jobExecutionStore, 1));
        lenient().when(jobExecutionStore.claimAttempt(any(ReportJobMessage.class), anyInt())).thenReturn(ClaimResult.STARTED);
        lenient().when(interviewReportParser.parseDraft(anyString())).thenReturn(defaultDraft);
        lenient().when(resumeImprovementPort.requireContext(anyLong(), anyLong()))
            .thenReturn(new ResumeImprovementPort.ImprovementContext(5L, 1, List.of()));
        lenient().when(resumeImprovementPort.storeSuggestions(anyLong(), anyLong(), anyLong(), anyList()))
            .thenReturn(List.of());
        lenient().when(interviewReportAssembler.assemble(any(), anyList(), anyList(), anyList()))
            .thenReturn(defaultReport);
        lenient().when(userWeaknessMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void handleReportJobFinishesSessionAndBroadcastsReportReady() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-1");

        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(99L);
        stage.setSessionId(7L);

        when(interviewSessionMapper.selectById(7L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# Report\",\"scores\":{\"technical\":8,\"expression\":7,\"logic\":9}}");
        when(devFixtureService.buildWeaknesses(42L, 7L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).updateById(sessionCaptor.capture());
        InterviewSession persisted = sessionCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo("finished");
        assertThat(persisted.getSummaryReport()).isEqualTo(defaultReportJson);

        ArgumentCaptor<InterviewStage> stageCaptor = ArgumentCaptor.forClass(InterviewStage.class);
        verify(interviewStageMapper).updateById(stageCaptor.capture());
        assertThat(stageCaptor.getValue().getEndedAt()).isNotNull();

        verify(scoreHistoryMapper, times(1)).insert(any(ScoreHistory.class));
        verify(interviewReportParser).parseDraft(anyString());
        verify(interviewReportAssembler).assemble(eq(defaultDraft), anyList(), anyList(), anyList());
        verify(sseEmitterRegistry).publish(eq(7L), eq("report_ready"), eq(defaultReportJson));
        verify(sseEmitterRegistry, never()).publish(anyLong(), eq("error"), anyString());
    }

    @Test
    void handleReportJobDeletesThenInsertsScoreHistory() {
        ReportJobMessage job = new ReportJobMessage(8L, 42L, "job-score");

        InterviewSession session = new InterviewSession();
        session.setId(8L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(101L);
        stage.setSessionId(8L);

        when(interviewSessionMapper.selectById(8L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(devFixtureService.buildWeaknesses(42L, 8L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        verify(scoreHistoryMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<ScoreHistory> scoreCaptor = ArgumentCaptor.forClass(ScoreHistory.class);
        verify(scoreHistoryMapper, times(1)).insert(scoreCaptor.capture());
        ScoreHistory score = scoreCaptor.getValue();
        assertThat(score.getUserId()).isEqualTo(42L);
        assertThat(score.getSessionId()).isEqualTo(8L);
        assertThat(score.getTechnicalScore()).isEqualTo(7);
        assertThat(score.getExpressionScore()).isEqualTo(6);
        assertThat(score.getLogicScore()).isEqualTo(8);
    }

    @Test
    void handleReportJobDeletesThenInsertsWeaknesses() {
        ReportJobMessage job = new ReportJobMessage(9L, 42L, "job-weakness");

        InterviewSession session = new InterviewSession();
        session.setId(9L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(102L);
        stage.setSessionId(9L);

        UserWeakness weakness = new UserWeakness();
        weakness.setUserId(42L);
        weakness.setSessionId(9L);
        weakness.setCategory("性能量化");
        weakness.setDescription("回答需要补充指标口径和压测数据。");

        when(interviewSessionMapper.selectById(9L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(devFixtureService.buildWeaknesses(42L, 9L)).thenReturn(List.of(weakness));
        when(userWeaknessMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(weakness));

        worker.handleReportJob(job);

        verify(userWeaknessMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<UserWeakness> weaknessCaptor = ArgumentCaptor.forClass(UserWeakness.class);
        verify(userWeaknessMapper, times(1)).insert(weaknessCaptor.capture());
        UserWeakness inserted = weaknessCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(42L);
        assertThat(inserted.getSessionId()).isEqualTo(9L);
        assertThat(inserted.getCategory()).isEqualTo("性能量化");
        assertThat(inserted.getDescription()).isEqualTo("回答需要补充指标口径和压测数据。");
        verify(sseEmitterRegistry, atLeastOnce()).publish(eq(9L), eq("report_ready"), anyString());
    }

    @Test
    void handleReportJobClearsUserContextInFinallyBlock() {
        ReportJobMessage job = new ReportJobMessage(10L, 42L, "job-cleanup");

        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setUserId(42L);
        session.setStatus("generating");
        session.setTargetPosition("Backend Engineer");

        InterviewStage stage = new InterviewStage();
        stage.setId(103L);
        stage.setSessionId(10L);

        when(interviewSessionMapper.selectById(10L)).thenReturn(session);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(interviewStageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stage);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenReturn("{\"reportMarkdown\":\"# R\",\"scores\":{\"technical\":7,\"expression\":6,\"logic\":8}}");
        when(devFixtureService.buildWeaknesses(42L, 10L)).thenReturn(Collections.<UserWeakness>emptyList());

        worker.handleReportJob(job);

        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentSessionId()).isNull();
    }

    @Test
    void handleReportJobRestoresOngoingAndBroadcastsErrorOnFailure() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-2");

        InterviewSession generating = new InterviewSession();
        generating.setId(7L);
        generating.setUserId(42L);
        generating.setStatus("generating");
        generating.setTargetPosition("Backend Engineer");

        InterviewSession sameRowAfterFailure = new InterviewSession();
        sameRowAfterFailure.setId(7L);
        sameRowAfterFailure.setUserId(42L);
        sameRowAfterFailure.setStatus("generating");
        sameRowAfterFailure.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(7L))
            .thenReturn(generating)
            .thenReturn(sameRowAfterFailure);
        when(interviewMessageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.<InterviewMessage>emptyList());
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveReport("Backend Engineer"))
            .thenThrow(new RuntimeException("LLM broker down"));

        worker.handleReportJob(job);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionMapper, times(1)).updateById(sessionCaptor.capture());
        InterviewSession restored = sessionCaptor.getValue();
        assertThat(restored.getStatus()).isEqualTo("ongoing");

        verify(sseEmitterRegistry).publish(eq(7L), eq("error"), anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionMissing() {
        ReportJobMessage job = new ReportJobMessage(404L, 42L, "job-3");
        when(interviewSessionMapper.selectById(404L)).thenReturn(null);

        worker.handleReportJob(job);

        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).publish(anyLong(), anyString(), anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionAlreadyFinished() {
        ReportJobMessage job = new ReportJobMessage(7L, 42L, "job-4");

        InterviewSession finishedSession = new InterviewSession();
        finishedSession.setId(7L);
        finishedSession.setUserId(42L);
        finishedSession.setStatus("finished");
        finishedSession.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(7L)).thenReturn(finishedSession);

        worker.handleReportJob(job);

        verify(interviewMessageMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(chatPort, never()).complete(any());
        verify(interviewReportParser, never()).parseDraft(anyString());
        verify(interviewReportAssembler, never()).assemble(any(), anyList(), anyList(), anyList());
        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).publish(anyLong(), eq("report_ready"), anyString());
        verify(sseEmitterRegistry, never()).publish(anyLong(), eq("error"), anyString());
        verify(devFixtureService, never()).isEnabled();
        verify(devFixtureService, never()).resolveReport(anyString());
    }

    @Test
    void handleReportJobSkipsWhenSessionOngoing() {
        ReportJobMessage job = new ReportJobMessage(11L, 42L, "job-ongoing");

        InterviewSession ongoingSession = new InterviewSession();
        ongoingSession.setId(11L);
        ongoingSession.setUserId(42L);
        ongoingSession.setStatus("ongoing");
        ongoingSession.setTargetPosition("Backend Engineer");

        when(interviewSessionMapper.selectById(11L)).thenReturn(ongoingSession);

        worker.handleReportJob(job);

        verify(interviewMessageMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(chatPort, never()).complete(any());
        verify(interviewReportParser, never()).parse(anyString());
        verify(scoreHistoryMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(scoreHistoryMapper, never()).insert(any(ScoreHistory.class));
        verify(userWeaknessMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(userWeaknessMapper, never()).insert(any(UserWeakness.class));
        verify(interviewSessionMapper, never()).updateById(any(InterviewSession.class));
        verify(sseEmitterRegistry, never()).publish(anyLong(), anyString(), anyString());
        verify(devFixtureService, never()).isEnabled();
    }

    @Test
    void handleReportJobClearsUserContextEvenWhenSessionMissing() {
        ReportJobMessage job = new ReportJobMessage(404L, 42L, "job-cleanup-missing");
        when(interviewSessionMapper.selectById(404L)).thenReturn(null);

        worker.handleReportJob(job);

        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(UserContext.getCurrentSessionId()).isNull();
    }

    private InterviewReportDraft draft(int technical, int expression, int logic, String markdown) {
        return new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("中等", "继续训练", "存在短板"),
            new InterviewReportDraft.DimensionScores(technical, expression, logic),
            List.of(),
            List.of("表达清楚"),
            new InterviewReportDraft.TrainingPlan(List.of("复盘"), List.of("专项"), List.of("量化")),
            "继续训练",
            markdown
        );
    }

    private StructuredInterviewReport report(int technical, int expression, int logic, String markdown) {
        return new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary("中等", "继续训练", "存在短板"),
            new StructuredInterviewReport.ReportScores(technical, expression, logic, 7.0),
            List.of(),
            List.of(),
            List.of("表达清楚"),
            List.of(),
            new StructuredInterviewReport.TrainingPlan(List.of("复盘"), List.of("专项"), List.of("量化")),
            "继续训练",
            markdown
        );
    }
}
