package com.prelude.artifact.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InterviewReportParserTest {

    private record WeaknessItem(String category, String description) {
    }

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

    @Test
    void readsAFencedArrayOfItems() {
        List<WeaknessItem> items = parser.parseItems("""
            ```json
            [{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
            ```""", WeaknessItem.class);

        assertThat(items).singleElement()
            .satisfies(item -> {
                assertThat(item.category()).isEqualTo("JVM 内存模型");
                assertThat(item.description()).isEqualTo("对堆、栈和 GC 场景回答不完整");
            });
    }

    @Test
    void recoversAnArrayTheModelWrappedInProse() {
        List<WeaknessItem> items = parser.parseItems(
            "候选人的薄弱点如下：[{\"category\":\"并发\",\"description\":\"缺少锁竞争分析\"}] 以上。",
            WeaknessItem.class);

        assertThat(items).singleElement()
            .satisfies(item -> assertThat(item.category()).isEqualTo("并发"));
    }

    @Test
    void rejectsAnObjectWhenAskedForItems() {
        assertThatThrownBy(() -> parser.parseItems("{\"category\":\"并发\"}", WeaknessItem.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("array");
    }
}
