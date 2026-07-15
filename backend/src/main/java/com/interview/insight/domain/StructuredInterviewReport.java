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
    String markdownFallback,
    List<ResumeImprovementSuggestion> resumeImprovements
) {

    public StructuredInterviewReport(
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
        this(
            summary, scores, stagePerformances, questionReviews, strengths, weaknesses,
            trainingPlan, finalAdvice, markdownFallback, List.of()
        );
    }

    public StructuredInterviewReport withResumeImprovements(List<ResumeImprovementSuggestion> suggestions) {
        return new StructuredInterviewReport(
            summary, scores, stagePerformances, questionReviews, strengths, weaknesses,
            trainingPlan, finalAdvice, markdownFallback, suggestions == null ? List.of() : List.copyOf(suggestions)
        );
    }

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

    public record ResumeImprovementSuggestion(
        Long id,
        Long resumeId,
        Long sessionId,
        String targetPath,
        String currentText,
        String proposedText,
        String rationale,
        String evidence,
        int baseDocumentVersion,
        String status
    ) {
    }
}
