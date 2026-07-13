package com.interview.bootstrap.dev;

import com.interview.bootstrap.dev.DevFixtureCatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.insight.domain.InterviewReportDraft;
import com.interview.insight.infrastructure.InterviewReportParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DevFixtureCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DevFixtureCatalog catalog = new DevFixtureCatalog(objectMapper);
    private final InterviewReportParser parser = new InterviewReportParser(objectMapper);

    @Test
    void allPositionReportsAreStructuredNarrativeDraftsWithoutDerivedFields() throws Exception {
        Map<String, int[]> expectedScores = Map.of(
            "Java 后端工程师", new int[]{7, 8, 7},
            "前端工程师", new int[]{8, 7, 7},
            "算法工程师", new int[]{7, 6, 8}
        );

        for (Map.Entry<String, int[]> entry : expectedScores.entrySet()) {
            String json = catalog.report(entry.getKey());
            JsonNode root = objectMapper.readTree(json);
            InterviewReportDraft draft = parser.parseDraft(json);

            assertThat(root.isObject()).isTrue();
            assertThat(root.has("summary")).isTrue();
            assertThat(root.has("weaknesses")).isFalse();
            assertThat(root.has("questionReviews")).isFalse();
            assertThat(root.path("scores").has("overall")).isFalse();
            assertThat(root.path("stagePerformances")).allSatisfy(stage ->
                assertThat(stage.has("score")).isFalse()
            );
            assertThat(draft.scores().technical()).isEqualTo(entry.getValue()[0]);
            assertThat(draft.scores().expression()).isEqualTo(entry.getValue()[1]);
            assertThat(draft.scores().logic()).isEqualTo(entry.getValue()[2]);
            assertThat(draft.reportMarkdown()).contains("# 面试评估报告");
        }
    }
}
