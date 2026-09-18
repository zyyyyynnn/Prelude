package com.prelude.artifact.application;

import com.prelude.test.ArtifactFixtures;
import com.prelude.llm.api.LlmPort;
import com.prelude.test.LlmFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GenerateInterviewReportResponseModeTest {

    @Test
    void reportUsesObjectSemanticsAndWeaknessExtractionUsesArraySemantics() {
        var reportPort = SessionFixtures.mockReportPort();
        var llmPort = LlmFixtures.mockResponseModePort();
        var parser = ArtifactFixtures.mockParser();
        var assembler = ArtifactFixtures.mockAssembler();
        var session = SessionFixtures.create(42L, 7L, "generating");
        session.setTargetPosition("Backend Engineer");
        session.setModelExecutionSnapshotId(99L);
        when(reportPort.findSession(42L)).thenReturn(session);
        when(reportPort.listMessages(42L)).thenReturn(List.of());
        when(reportPort.listStages(42L)).thenReturn(List.of());

        GenerateInterviewReport generate = new GenerateInterviewReport(
            new ObjectMapper(), reportPort, llmPort, parser, assembler);

        GenerateInterviewReport.GenerationResult result = generate.execute(42L, 7L);
        assertThat(result.outcome()).isEqualTo(GenerateInterviewReport.Outcome.GENERATED);
        assertThat(result.reportJson()).isNotBlank();

        List<LlmPort.ResponseMode> responseModes = LlmFixtures.captureResponseModes(llmPort, 2);
        assertThat(responseModes).containsExactly(
            LlmFixtures.responseModeJsonObject(),
            LlmFixtures.responseModeJsonArray()
        );
    }
}