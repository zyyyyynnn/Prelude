package com.prelude.artifact.application;

import com.prelude.artifact.infrastructure.InterviewReportParser;
import com.prelude.test.ArtifactFixtures;
import com.prelude.llm.api.LlmPort;
import com.prelude.test.LlmFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerateInterviewReportResponseModeTest {

    @Test
    void reportUsesObjectSemanticsAndWeaknessExtractionUsesArraySemantics() {
        var reportPort = SessionFixtures.mockReportPort();
        var llmPort = LlmFixtures.mockResponseModePort();
        var parser = ArtifactFixtures.mockParser();
        var assembler = ArtifactFixtures.mockAssembler();
        var session = SessionFixtures.reportSession(42L, 7L, "generating", "Backend Engineer", 99L);
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

    @Test
    void weaknessExtractionReadsTheFencedArrayThroughTheParser() {
        var reportPort = SessionFixtures.mockReportPort();
        var llmPort = mock(LlmPort.class);
        when(llmPort.complete(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            LlmPort.ModelExecutionRequest request = invocation.getArgument(0);
            String content = request.responseMode() == LlmPort.ResponseMode.JSON_ARRAY
                ? """
                ```json
                [{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
                ```"""
                : """
                {
                  "summary": {
                    "fitAssessment": "继续投递",
                    "actionRecommendation": "补强后复试",
                    "overallRisk": "项目量化不足"
                  },
                  "scores": {"technical": 9, "expression": 7, "logic": 8},
                  "stagePerformances": [],
                  "strengths": [],
                  "trainingPlan": {"threeDay": [], "sevenDay": [], "nextInterviewFocus": []},
                  "finalAdvice": "继续训练",
                  "reportMarkdown": "原始报告"
                }""";
            return new LlmPort.CompletionResult(content, null);
        });
        var assembler = ArtifactFixtures.mockAssembler();
        var session = SessionFixtures.reportSession(42L, 7L, "generating", "Backend Engineer", 99L);
        when(reportPort.findSession(42L)).thenReturn(session);
        when(reportPort.listMessages(42L)).thenReturn(List.of());
        when(reportPort.listStages(42L)).thenReturn(List.of());

        GenerateInterviewReport generate = new GenerateInterviewReport(
            new ObjectMapper(), reportPort, llmPort, new InterviewReportParser(new ObjectMapper()), assembler);

        GenerateInterviewReport.GenerationResult result = generate.execute(42L, 7L);
        assertThat(result.weaknesses()).singleElement()
            .satisfies(weakness -> {
                assertThat(weakness.getCategory()).isEqualTo("JVM 内存模型");
                assertThat(weakness.getDescription()).isEqualTo("对堆、栈和 GC 场景回答不完整");
            });
    }
}