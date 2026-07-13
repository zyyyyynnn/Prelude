package com.interview.insight.domain;

import java.util.List;

public record StructuredInterviewReport(
    ReportSummary summary,
    ReportScores scores,
    List<StagePerformance> stagePerformances,
    List<QuestionReview> questionReviews,
    List<String> strengths,
    List<String> weaknesses,
    TrainingPlan trainingPlan,
    String finalAdvice,
    String markdownFallback
) {

    public record ReportSummary(
        String fitAssessment,
        String actionRecommendation,
        String overallRisk
    ) {
    }

    public record ReportScores(
        int technical,
        int expression,
        int logic,
        double overall
    ) {
    }

    public record StagePerformance(
        String stageName,
        Double score,
        String summary,
        List<String> positiveSignals,
        List<String> negativeSignals,
        List<String> improvementSuggestions
    ) {
    }

    public record QuestionReview(
        String stageName,
        String question,
        String answerSummary,
        Integer score,
        String scoringReason,
        String improvementSuggestion
    ) {
    }

    public record TrainingPlan(
        List<String> threeDay,
        List<String> sevenDay,
        List<String> nextInterviewFocus
    ) {
    }
}
