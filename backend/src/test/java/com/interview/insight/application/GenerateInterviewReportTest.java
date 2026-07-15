package com.interview.insight.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.insight.application.port.InsightFixturePort;
import com.interview.insight.application.port.InsightRepository;
import com.interview.insight.domain.InterviewReportAssembler;
import com.interview.insight.domain.InterviewReportDraft;
import com.interview.insight.domain.ReportParser;
import com.interview.insight.domain.StructuredInterviewReport;
import com.interview.insight.domain.UserWeakness;
import com.interview.interview.api.port.InterviewReportPort;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.realtime.RealtimePort;
import com.interview.shared.web.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateInterviewReportTest {

    @Mock private InterviewReportPort interviewReportPort;
    @Mock private InsightRepository insightRepository;
    @Mock private ChatPort chatPort;
    @Mock private InsightFixturePort fixturePort;
    @Mock private ReportParser reportParser;
    @Mock private InterviewReportAssembler reportAssembler;
    @Mock private RealtimePort realtimePort;

    private GenerateInterviewReport useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateInterviewReport(
            new ObjectMapper(),
            interviewReportPort,
            insightRepository,
            chatPort,
            fixturePort,
            reportParser,
            reportAssembler,
            realtimePort
        );
    }

    @AfterEach
    void clearUserContext() {
        UserContext.remove();
    }

    @Test
    void skipsMissingOrStaleSessionAndAlwaysClearsUserContext() {
        when(interviewReportPort.findSession(7L)).thenReturn(null);

        assertThat(useCase.execute(7L, 42L)).isEqualTo(GenerateInterviewReport.Outcome.SKIPPED);
        assertThat(UserContext.getCurrentUserId()).isNull();

        InterviewSession stale = session("ongoing");
        when(interviewReportPort.findSession(8L)).thenReturn(stale);
        assertThat(useCase.execute(8L, 42L)).isEqualTo(GenerateInterviewReport.Outcome.SKIPPED);
        verify(interviewReportPort, never()).listMessages(8L);
    }

    @Test
    void generatesStructuredReportPersistsInsightsAndPublishesReadyEvent() {
        InterviewSession session = session("generating");
        InterviewMessage system = message("system", "hidden prompt");
        InterviewMessage answer = message("user", "candidate answer");
        InterviewReportDraft draft = draft();
        StructuredInterviewReport structured = structuredReport();
        when(interviewReportPort.findSession(7L)).thenReturn(session);
        when(interviewReportPort.listMessages(7L)).thenReturn(List.of(system, answer));
        when(fixturePort.isEnabled()).thenReturn(false);
        when(chatPort.complete(any(ChatRequest.class)))
            .thenReturn("raw report")
            .thenReturn("prefix [{\"category\":\" JVM \" ,\"description\":\" GC 不完整 \"},"
                + "{\"category\":\"\",\"description\":\"ignored\"}] suffix");
        when(reportParser.parseDraft("raw report")).thenReturn(draft);
        when(insightRepository.listWeaknessesBySession(7L)).thenReturn(List.of());
        when(reportAssembler.assemble(draft, List.of(), List.of(system, answer), List.of()))
            .thenReturn(structured);
        when(interviewReportPort.listStages(7L)).thenReturn(List.of());

        GenerateInterviewReport.Outcome outcome = useCase.execute(7L, 42L);

        assertThat(outcome).isEqualTo(GenerateInterviewReport.Outcome.COMPLETED);
        verify(interviewReportPort).closeCurrentStage(7L);
        verify(insightRepository).replaceScore(any());
        ArgumentCaptor<List<UserWeakness>> weaknessesCaptor = ArgumentCaptor.forClass(List.class);
        verify(insightRepository).replaceWeaknesses(org.mockito.ArgumentMatchers.eq(7L), weaknessesCaptor.capture());
        assertThat(weaknessesCaptor.getValue()).singleElement().satisfies(weakness -> {
            assertThat(weakness.getCategory()).isEqualTo("JVM");
            assertThat(weakness.getDescription()).isEqualTo("GC 不完整");
        });
        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(interviewReportPort).completeReport(org.mockito.ArgumentMatchers.eq(7L), reportCaptor.capture());
        assertThat(reportCaptor.getValue()).contains("\"technical\":8", "candidate advice");
        verify(realtimePort).publish(7L, "report_ready", reportCaptor.getValue());

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatPort, org.mockito.Mockito.times(2)).complete(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().getFirst().messages().get(1).get("content"))
            .contains("candidate answer")
            .doesNotContain("hidden prompt");
    }

    @Test
    void wrapsGenerationFailureAndClearsUserContext() {
        when(interviewReportPort.findSession(7L)).thenThrow(new RuntimeException("database down"));

        assertThatThrownBy(() -> useCase.execute(7L, 42L))
            .isInstanceOf(GenerateInterviewReport.ReportGenerationException.class)
            .hasMessageContaining("database down");
        assertThat(UserContext.getCurrentUserId()).isNull();
        assertThat(UserContext.getCurrentSessionId()).isNull();
    }

    @Test
    void terminalFailurePublishesErrorAndRestoresOngoingStatus() {
        RuntimeException failure = new RuntimeException("llm down");

        useCase.handleTerminalFailure(7L, failure);

        verify(realtimePort).publish(7L, "error", "报告生成失败: llm down");
        verify(interviewReportPort).restoreOngoing(7L);
    }

    private InterviewSession session(String status) {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus(status);
        session.setTargetPosition("Java 开发");
        session.setLlmProvider("openai-responses");
        session.setLlmModel("model-a");
        return session;
    }

    private InterviewMessage message(String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private InterviewReportDraft draft() {
        return new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("fit", "action", "risk"),
            new InterviewReportDraft.DimensionScores(8, 7, 6),
            List.of(),
            List.of("strength"),
            new InterviewReportDraft.TrainingPlan(List.of("3d"), List.of("7d"), List.of("next")),
            "candidate advice",
            "report markdown"
        );
    }

    private StructuredInterviewReport structuredReport() {
        return new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary("fit", "action", "risk"),
            new StructuredInterviewReport.ReportScores(8, 7, 6, 7.0),
            List.of(),
            List.of(),
            List.of("strength"),
            List.of("weakness"),
            new StructuredInterviewReport.TrainingPlan(List.of("3d"), List.of("7d"), List.of("next")),
            "candidate advice",
            "report markdown"
        );
    }
}
