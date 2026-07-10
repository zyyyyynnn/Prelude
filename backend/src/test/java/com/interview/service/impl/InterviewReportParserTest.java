package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.dto.InterviewReportDraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewReportParserTest {

    private final InterviewReportParser parser = new InterviewReportParser(new ObjectMapper());

    @Test
    void parsesNarrativeDraftAndClampsDimensionScores() {
        InterviewReportDraft draft = parser.parseDraft("""
            {
              "summary": {
                "fitAssessment": "继续投递",
                "actionRecommendation": "补强后复试",
                "overallRisk": "项目量化不足"
              },
              "scores": {
                "technical": 15,
                "expression": 0,
                "logic": 8
              },
              "stagePerformances": [{
                "stageName": "technical",
                "summary": "基础稳定",
                "positiveSignals": ["结构清楚"],
                "negativeSignals": ["缺少数据"],
                "improvementSuggestions": ["补充指标"]
              }],
              "strengths": ["结构化表达"],
              "trainingPlan": {
                "threeDay": ["复盘回答"],
                "sevenDay": ["专项训练"],
                "nextInterviewFocus": ["量化表达"]
              },
              "finalAdvice": "继续训练",
              "reportMarkdown": "# 面试评估报告"
            }
            """);

        assertThat(draft.summary().fitAssessment()).isEqualTo("继续投递");
        assertThat(draft.scores().technical()).isEqualTo(10);
        assertThat(draft.scores().expression()).isEqualTo(1);
        assertThat(draft.scores().logic()).isEqualTo(8);
        assertThat(draft.stagePerformances()).singleElement().satisfies(stage -> {
            assertThat(stage.stageName()).isEqualTo("technical");
            assertThat(stage.positiveSignals()).containsExactly("结构清楚");
        });
        assertThat(draft.trainingPlan().threeDay()).containsExactly("复盘回答");
        assertThat(draft.reportMarkdown()).isEqualTo("# 面试评估报告");
    }

    @Test
    void fillsMissingNarrativeFieldsWithoutFailing() {
        InterviewReportDraft draft = parser.parseDraft("""
            {"scores":{"technical":8,"expression":7,"logic":9}}
            """);

        assertThat(draft.summary()).isNotNull();
        assertThat(draft.summary().fitAssessment()).isNotBlank();
        assertThat(draft.stagePerformances()).isEmpty();
        assertThat(draft.strengths()).isEmpty();
        assertThat(draft.trainingPlan().threeDay()).isEmpty();
        assertThat(draft.finalAdvice()).isNotBlank();
        assertThat(draft.reportMarkdown()).isNotBlank();
    }

    @Test
    void fallsBackToSafeDraftAndRawMarkdownWhenJsonCannotBeParsed() {
        InterviewReportDraft draft = parser.parseDraft("# 非结构化报告");

        assertThat(draft.reportMarkdown()).isEqualTo("# 非结构化报告");
        assertThat(draft.scores().technical()).isEqualTo(6);
        assertThat(draft.scores().expression()).isEqualTo(6);
        assertThat(draft.scores().logic()).isEqualTo(6);
        assertThat(draft.summary().overallRisk()).isNotBlank();
    }

    @Test
    void stripsJsonFence() {
        InterviewReportDraft draft = parser.parseDraft("""
            ```json
            {"reportMarkdown":"# 报告","scores":{"technical":9,"expression":8,"logic":7}}
            ```
            """);

        assertThat(draft.scores().technical()).isEqualTo(9);
        assertThat(draft.reportMarkdown()).isEqualTo("# 报告");
    }
}
