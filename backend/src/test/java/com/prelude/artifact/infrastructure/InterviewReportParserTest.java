package com.prelude.artifact.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InterviewReportParserTest {

    private final InterviewReportParser parser = new InterviewReportParser(new ObjectMapper());

    @Test
    void parsesACompleteStructuredReportWithoutChangingBusinessValues() {
        var draft = parser.parseDraft("""
            {
              "summary": {
                "fitAssessment": "继续投递",
                "actionRecommendation": "补强后复试",
                "overallRisk": "项目量化不足"
              },
              "scores": {"technical": 9, "expression": 7, "logic": 8},
              "stagePerformances": [],
              "strengths": ["结构化表达"],
              "trainingPlan": {"threeDay": [], "sevenDay": [], "nextInterviewFocus": []},
              "finalAdvice": "继续训练",
              "reportMarkdown": "原始报告"
            }
            """);

        assertThat(draft.scores().technical()).isEqualTo(9);
        assertThat(draft.scores().expression()).isEqualTo(7);
        assertThat(draft.scores().logic()).isEqualTo(8);
    }

    @Test
    void rejectsMissingOrOutOfRangeScoresInsteadOfInventingValidFacts() {
        assertThatThrownBy(() -> parser.parseDraft("""
            {
              "summary": {
                "fitAssessment": "继续投递",
                "actionRecommendation": "补强后复试",
                "overallRisk": "项目量化不足"
              },
              "scores": {"expression": 7, "logic": 8},
              "finalAdvice": "继续训练",
              "reportMarkdown": "原始报告"
            }
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("technical");

        assertThatThrownBy(() -> parser.parseDraft("""
            {
              "summary": {
                "fitAssessment": "继续投递",
                "actionRecommendation": "补强后复试",
                "overallRisk": "项目量化不足"
              },
              "scores": {"technical": 11, "expression": 7, "logic": 8},
              "finalAdvice": "继续训练",
              "reportMarkdown": "原始报告"
            }
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("technical");
    }
}
