package com.interview.insight.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.insight.domain.InterviewReportDraft;
import com.interview.insight.domain.ReportParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class InterviewReportParser implements ReportParser {

    private static final int FALLBACK_SCORE = 6;
    private static final String FALLBACK_MARKDOWN = "# 面试训练报告\n\n报告叙述字段不完整，请结合逐题复盘继续训练。";

    private final ObjectMapper objectMapper;

    public InterviewReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedReport parse(String content) {
        InterviewReportDraft draft = parseDraft(content);
        return new ParsedReport(
            draft.reportMarkdown(),
            draft.scores().technical(),
            draft.scores().expression(),
            draft.scores().logic()
        );
    }

    @Override
    public InterviewReportDraft parseDraft(String content) {
        String rawContent = content == null ? "" : content.trim();
        String jsonContent = stripJsonFence(rawContent);
        if (!jsonContent.startsWith("{")) {
            return fallback(rawContent);
        }
        try {
            return normalize(objectMapper.readValue(jsonContent, InterviewReportDraft.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Failed to parse structured interview report draft: {}", exception.getMessage());
            return fallback(rawContent);
        }
    }

    private InterviewReportDraft normalize(InterviewReportDraft report) {
        InterviewReportDraft.ReportSummary sourceSummary = report.summary();
        InterviewReportDraft.ReportSummary summary = new InterviewReportDraft.ReportSummary(
            text(sourceSummary == null ? null : sourceSummary.fitAssessment(), "建议结合岗位要求继续评估"),
            text(sourceSummary == null ? null : sourceSummary.actionRecommendation(), "针对薄弱项训练后再次模拟"),
            text(sourceSummary == null ? null : sourceSummary.overallRisk(), "现有信息不足，需结合逐题表现判断")
        );

        InterviewReportDraft.DimensionScores sourceScores = report.scores();
        InterviewReportDraft.DimensionScores scores = new InterviewReportDraft.DimensionScores(
            clamp(sourceScores == null ? null : sourceScores.technical()),
            clamp(sourceScores == null ? null : sourceScores.expression()),
            clamp(sourceScores == null ? null : sourceScores.logic())
        );

        List<InterviewReportDraft.StageNarrative> stages = safeList(report.stagePerformances()).stream()
            .filter(Objects::nonNull)
            .map(stage -> new InterviewReportDraft.StageNarrative(
                text(stage.stageName(), "unknown"),
                text(stage.summary(), "本阶段暂无补充总结"),
                strings(stage.positiveSignals()),
                strings(stage.negativeSignals()),
                strings(stage.improvementSuggestions())
            ))
            .toList();

        InterviewReportDraft.TrainingPlan sourcePlan = report.trainingPlan();
        InterviewReportDraft.TrainingPlan plan = new InterviewReportDraft.TrainingPlan(
            strings(sourcePlan == null ? null : sourcePlan.threeDay()),
            strings(sourcePlan == null ? null : sourcePlan.sevenDay()),
            strings(sourcePlan == null ? null : sourcePlan.nextInterviewFocus())
        );

        return new InterviewReportDraft(
            summary,
            scores,
            stages,
            strings(report.strengths()),
            plan,
            text(report.finalAdvice(), "保持复盘，并围绕薄弱项进行下一轮专项训练。"),
            text(report.reportMarkdown(), FALLBACK_MARKDOWN)
        );
    }

    private InterviewReportDraft fallback(String rawContent) {
        return normalize(new InterviewReportDraft(
            null,
            new InterviewReportDraft.DimensionScores(FALLBACK_SCORE, FALLBACK_SCORE, FALLBACK_SCORE),
            List.of(),
            List.of(),
            null,
            null,
            rawContent.isBlank() ? FALLBACK_MARKDOWN : rawContent
        ));
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private int clamp(Integer value) {
        if (value == null) {
            return FALLBACK_SCORE;
        }
        return Math.max(1, Math.min(10, value));
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> strings(List<String> values) {
        return safeList(values).stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

}
