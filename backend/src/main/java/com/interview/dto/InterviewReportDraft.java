package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewReportDraft(
    ReportSummary summary,
    DimensionScores scores,
    List<StageNarrative> stagePerformances,
    List<String> strengths,
    TrainingPlan trainingPlan,
    String finalAdvice,
    String reportMarkdown
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportSummary(
        String fitAssessment,
        String actionRecommendation,
        String overallRisk
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DimensionScores(
        Integer technical,
        Integer expression,
        Integer logic
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageNarrative(
        String stageName,
        String summary,
        List<String> positiveSignals,
        List<String> negativeSignals,
        List<String> improvementSuggestions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrainingPlan(
        List<String> threeDay,
        List<String> sevenDay,
        List<String> nextInterviewFocus
    ) {
    }
}
