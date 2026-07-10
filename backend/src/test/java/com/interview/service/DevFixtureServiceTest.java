package com.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.config.DevFixtureProperties;
import com.interview.dto.InterviewReportDraft;
import com.interview.dto.StructuredInterviewReport;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.PositionTemplate;
import com.interview.entity.Resume;
import com.interview.entity.User;
import com.interview.entity.UserWeakness;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.service.impl.InterviewReportAssembler;
import com.interview.service.impl.InterviewReportParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevFixtureServiceTest {

    @Mock private DevFixtureProperties properties;
    @Mock private DevFixtureCatalog catalog;
    @Mock private UserMapper userMapper;
    @Mock private ResumeMapper resumeMapper;
    @Mock private PositionTemplateMapper positionTemplateMapper;
    @Mock private InterviewSessionMapper sessionMapper;
    @Mock private InterviewMessageMapper messageMapper;
    @Mock private InterviewStageMapper stageMapper;
    @Mock private ScoreHistoryMapper scoreHistoryMapper;
    @Mock private UserWeaknessMapper weaknessMapper;
    @Mock private InterviewReportAssembler reportAssembler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<InterviewMessage> insertedMessages = new ArrayList<>();
    private final List<InterviewStage> insertedStages = new ArrayList<>();
    private final List<UserWeakness> insertedWeaknesses = new ArrayList<>();
    private final List<InterviewSession> insertedSessions = new ArrayList<>();
    private DevFixtureService service;

    @BeforeEach
    void setUp() throws Exception {
        InterviewReportParser parser = new InterviewReportParser(objectMapper);
        service = new DevFixtureService(
            properties, catalog, objectMapper, userMapper, resumeMapper, positionTemplateMapper,
            sessionMapper, messageMapper, stageMapper, scoreHistoryMapper, weaknessMapper,
            parser, reportAssembler
        );

        when(properties.isEnabled()).thenReturn(true);
        User user = new User();
        user.setId(42L);
        user.setUsername("demo");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(sessionMapper.selectList(any())).thenReturn(List.of());
        when(catalog.llmConfig()).thenReturn(new DevFixtureCatalog.DevLlmConfigFixture("deepseek", "deepseek-chat", "****dev"));
        when(catalog.resume(anyString())).thenReturn(new DevFixtureCatalog.DevResumeFixture(
            List.of("Java"), List.of(), "简历原文"
        ));

        PositionTemplate java = position(1L, "Java 后端工程师");
        PositionTemplate frontend = position(2L, "前端工程师");
        PositionTemplate algorithm = position(3L, "算法工程师");
        when(positionTemplateMapper.selectOne(any())).thenReturn(java, frontend, algorithm);

        List<DevFixtureCatalog.QnaPair> script = List.of(
            new DevFixtureCatalog.QnaPair("warmup", null, "破冰问题", "破冰回答"),
            new DevFixtureCatalog.QnaPair("technical", "进入技术阶段", "技术问题", "技术回答"),
            new DevFixtureCatalog.QnaPair("deep_dive", "进入深挖阶段", "深挖问题", "深挖回答"),
            new DevFixtureCatalog.QnaPair("closing", "进入收尾阶段", "收尾问题", "收尾回答")
        );
        when(catalog.javaScript()).thenReturn(script);
        when(catalog.frontendScript()).thenReturn(script);
        when(catalog.algorithmScript()).thenReturn(script);
        when(catalog.javaStorylineWeaknesses()).thenReturn(List.of(new DevFixtureCatalog.WeaknessSeed("量化", "缺少指标")));
        when(catalog.frontendStorylineWeaknesses()).thenReturn(List.of(new DevFixtureCatalog.WeaknessSeed("边界", "边界不清")));
        when(catalog.algorithmStorylineWeaknesses()).thenReturn(List.of(new DevFixtureCatalog.WeaknessSeed("实验", "实验不足")));

        String draftJson = """
            {"summary":{"fitAssessment":"中等","actionRecommendation":"继续训练","overallRisk":"存在短板"},
             "scores":{"technical":8,"expression":7,"logic":9},"stagePerformances":[],
             "strengths":["结构清楚"],"trainingPlan":{"threeDay":[],"sevenDay":[],"nextInterviewFocus":[]},
             "finalAdvice":"继续训练","reportMarkdown":"# 报告"}
            """;
        when(catalog.report(anyString())).thenReturn(draftJson);
        when(catalog.javaOngoingUserAnswer()).thenReturn("进行中的回答");
        when(catalog.scriptedReply(anyString(), any(Integer.class))).thenReturn("面试官问题");

        AtomicLong resumeId = new AtomicLong(10);
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(resumeId.getAndIncrement());
            return 1;
        });
        AtomicLong sessionId = new AtomicLong(100);
        when(sessionMapper.insert(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(sessionId.getAndIncrement());
            insertedSessions.add(session);
            return 1;
        });
        when(messageMapper.insert(any(InterviewMessage.class))).thenAnswer(invocation -> {
            insertedMessages.add(invocation.getArgument(0));
            return 1;
        });
        when(stageMapper.insert(any(InterviewStage.class))).thenAnswer(invocation -> {
            insertedStages.add(invocation.getArgument(0));
            return 1;
        });
        when(weaknessMapper.insert(any(UserWeakness.class))).thenAnswer(invocation -> {
            insertedWeaknesses.add(invocation.getArgument(0));
            return 1;
        });
        when(messageMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(insertedMessages));
        when(stageMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(insertedStages));
        when(weaknessMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(insertedWeaknesses));

        StructuredInterviewReport assembled = new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary("中等", "继续训练", "存在短板"),
            new StructuredInterviewReport.ReportScores(8, 7, 9, 8.0),
            List.of(), List.of(), List.of(), List.of(),
            new StructuredInterviewReport.TrainingPlan(List.of(), List.of(), List.of()),
            "继续训练", "# 报告"
        );
        when(reportAssembler.assemble(any(InterviewReportDraft.class), any(), any(), any())).thenReturn(assembled);
    }

    @Test
    void resetSeedsScoredUserMessagesWithinStageWindowsAndUsesSharedAssembler() {
        service.reset();

        Long ongoingSessionId = insertedSessions.stream()
            .filter(session -> "ongoing".equals(session.getStatus()))
            .map(InterviewSession::getId)
            .findFirst()
            .orElseThrow();
        assertThat(insertedMessages.stream()
            .filter(message -> ongoingSessionId.equals(message.getSessionId()))
            .filter(message -> "user".equals(message.getRole())))
            .singleElement()
            .satisfies(message -> {
                assertThat(message.getScore()).isBetween(7, 9);
                assertThat(message.getHint()).isNotBlank();
            });

        List<Long> finishedSessionIds = insertedSessions.stream()
            .filter(session -> "finished".equals(session.getStatus()))
            .map(InterviewSession::getId)
            .toList();
        List<InterviewMessage> finishedUserMessages = insertedMessages.stream()
            .filter(message -> finishedSessionIds.contains(message.getSessionId()))
            .filter(message -> "user".equals(message.getRole()))
            .toList();

        assertThat(finishedUserMessages).hasSize(12).allSatisfy(message -> {
            assertThat(message.getScore()).isBetween(7, 9);
            assertThat(message.getHint()).isNotBlank();
            assertThat(insertedStages).anySatisfy(stage -> {
                if (stage.getSessionId().equals(message.getSessionId())
                    && contains(stage.getStartedAt(), stage.getEndedAt(), message.getCreatedAt())) {
                    assertThat(stage.getStageName()).isNotBlank();
                } else {
                    throw new AssertionError("not matching stage");
                }
            });
        });
        verify(reportAssembler, times(3)).assemble(any(), any(), any(), any());
        assertThat(insertedSessions.stream()
            .filter(session -> "finished".equals(session.getStatus()))
            .map(InterviewSession::getSummaryReport))
            .allMatch(report -> report != null && report.startsWith("{"));
    }

    private boolean contains(LocalDateTime start, LocalDateTime end, LocalDateTime value) {
        return value != null && !value.isBefore(start) && (end == null || value.isBefore(end));
    }

    private PositionTemplate position(Long id, String name) {
        PositionTemplate position = new PositionTemplate();
        position.setId(id);
        position.setName(name);
        position.setSystemPrompt("系统提示");
        return position;
    }
}
