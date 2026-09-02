package com.prelude.artifact.application;

import com.prelude.artifact.domain.InterviewReportAssembler;
import com.prelude.artifact.domain.InterviewReportDraft;
import com.prelude.artifact.domain.ReportParser;
import com.prelude.artifact.domain.StructuredInterviewReport;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.LlmPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class GenerateInterviewReportResponseModeTest {

    @Test
    void reportUsesObjectSemanticsAndWeaknessExtractionUsesArraySemantics() {
        InterviewReportPort reportPort = mock(InterviewReportPort.class);
        LlmPort llmPort = mock(LlmPort.class);
        ReportParser parser = mock(ReportParser.class);
        InterviewReportAssembler assembler = mock(InterviewReportAssembler.class);
        InterviewSession session = new InterviewSession();
        session.setId(42L);
        session.setAccountId(7L);
        session.setTargetPosition("Backend Engineer");
        session.setModelExecutionSnapshotId(99L);
        session.setStatus("generating");
        when(reportPort.findSession(42L)).thenReturn(session);
        when(reportPort.listMessages(42L)).thenReturn(List.of());
        when(reportPort.listStages(42L)).thenReturn(List.of());
        when(llmPort.complete(any())).thenAnswer(invocation -> {
            LlmPort.ModelExecutionRequest request = invocation.getArgument(0);
            return new LlmPort.CompletionResult(
                request.responseMode() == LlmPort.ResponseMode.JSON_ARRAY ? "[]" : "{}",
                null
            );
        });
        InterviewReportDraft draft = new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("fit", "act", "risk"),
            new InterviewReportDraft.DimensionScores(8, 8, 8),
            List.of(),
            List.of(),
            new InterviewReportDraft.TrainingPlan(List.of(), List.of(), List.of()),
            "advice",
            "report"
        );
        when(parser.parseDraft("{}")).thenReturn(draft);
        StructuredInterviewReport report = new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary("fit", "act", "risk"),
            new StructuredInterviewReport.ReportScores(8, 8, 8, 8.0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new StructuredInterviewReport.TrainingPlan(List.of(), List.of(), List.of()),
            "advice"
        );
        when(assembler.assemble(any(), any(), any(), any())).thenReturn(report);
        GenerateInterviewReport generate = new GenerateInterviewReport(
            new ObjectMapper(), reportPort, llmPort, parser, assembler);

        GenerateInterviewReport.GenerationResult result = generate.execute(42L, 7L);
        assertThat(result.outcome()).isEqualTo(GenerateInterviewReport.Outcome.GENERATED);
        assertThat(result.reportJson()).isNotBlank();

        ArgumentCaptor<LlmPort.ModelExecutionRequest> requests =
            ArgumentCaptor.forClass(LlmPort.ModelExecutionRequest.class);
        verify(llmPort, org.mockito.Mockito.times(2)).complete(requests.capture());
        assertThat(requests.getAllValues())
            .extracting(LlmPort.ModelExecutionRequest::responseMode)
            .containsExactly(LlmPort.ResponseMode.JSON_OBJECT, LlmPort.ResponseMode.JSON_ARRAY);
        verify(reportPort, never()).closeCurrentStage(42L);
        verify(reportPort, never()).completeReport(org.mockito.ArgumentMatchers.eq(42L), any());
    }
}
