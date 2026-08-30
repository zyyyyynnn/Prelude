package com.prelude.artifact.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prelude.artifact.domain.InterviewReportDraft;
import com.prelude.artifact.domain.ReportParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class InterviewReportParser implements ReportParser {

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
            throw new IllegalArgumentException("interview report must be structured JSON");
        }
        try {
            return normalize(objectMapper.readValue(jsonContent, InterviewReportDraft.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("interview report JSON is malformed", exception);
        }
    }

    private InterviewReportDraft normalize(InterviewReportDraft report) {
        InterviewReportDraft.ReportSummary sourceSummary = report.summary();
        if (sourceSummary == null) {
            throw new IllegalArgumentException("summary is required");
        }
        InterviewReportDraft.ReportSummary summary = new InterviewReportDraft.ReportSummary(
            requiredText(sourceSummary.fitAssessment(), "summary.fitAssessment"),
            requiredText(sourceSummary.actionRecommendation(), "summary.actionRecommendation"),
            requiredText(sourceSummary.overallRisk(), "summary.overallRisk")
        );

        InterviewReportDraft.DimensionScores sourceScores = report.scores();
        if (sourceScores == null) {
            throw new IllegalArgumentException("scores are required");
        }
        InterviewReportDraft.DimensionScores scores = new InterviewReportDraft.DimensionScores(
            requiredScore(sourceScores.technical(), "technical"),
            requiredScore(sourceScores.expression(), "expression"),
            requiredScore(sourceScores.logic(), "logic")
        );

        List<InterviewReportDraft.StageNarrative> stages = safeList(report.stagePerformances()).stream()
            .filter(Objects::nonNull)
            .filter(stage -> stage.stageName() != null && !stage.stageName().isBlank())
            .filter(stage -> stage.summary() != null && !stage.summary().isBlank())
            .map(stage -> new InterviewReportDraft.StageNarrative(
                stage.stageName().trim(),
                stage.summary().trim(),
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
            requiredText(report.finalAdvice(), "finalAdvice"),
            requiredText(report.reportMarkdown(), "reportMarkdown")
        );
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

    private int requiredScore(Integer value, String field) {
        if (value == null || value < 1 || value > 10) {
            throw new IllegalArgumentException(field + " score must be between 1 and 10");
        }
        return value;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
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
