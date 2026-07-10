package com.interview.service.impl;

import com.interview.dto.InterviewReportDraft;
import com.interview.dto.StructuredInterviewReport;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewStage;
import com.interview.entity.UserWeakness;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class InterviewReportAssembler {

    private static final List<String> STAGE_ORDER = List.of("warmup", "technical", "deep_dive", "closing");
    private static final int ANSWER_SUMMARY_LIMIT = 180;

    public StructuredInterviewReport assemble(
        InterviewReportDraft draft,
        List<InterviewStage> stages,
        List<InterviewMessage> messages,
        List<UserWeakness> weaknesses
    ) {
        InterviewReportDraft safeDraft = Objects.requireNonNull(draft, "report draft must not be null");
        List<InterviewStage> orderedStages = safeList(stages).stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(InterviewStage::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        List<InterviewMessage> orderedMessages = safeList(messages).stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(InterviewMessage::getSeqNum, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        Map<String, InterviewReportDraft.StageNarrative> narratives = new LinkedHashMap<>();
        for (InterviewReportDraft.StageNarrative narrative : safeList(safeDraft.stagePerformances())) {
            if (narrative != null && STAGE_ORDER.contains(narrative.stageName())) {
                narratives.put(narrative.stageName(), narrative);
            }
        }

        List<StructuredInterviewReport.QuestionReview> questionReviews = buildQuestionReviews(
            orderedStages, orderedMessages, narratives
        );
        List<StructuredInterviewReport.StagePerformance> stagePerformances = STAGE_ORDER.stream()
            .map(stageName -> buildStagePerformance(stageName, narratives.get(stageName), questionReviews))
            .toList();

        InterviewReportDraft.DimensionScores dimensionScores = safeDraft.scores();
        int technical = dimensionScores.technical();
        int expression = dimensionScores.expression();
        int logic = dimensionScores.logic();
        double overall = oneDecimal((technical + expression + logic) / 3.0);

        InterviewReportDraft.ReportSummary summary = safeDraft.summary();
        InterviewReportDraft.TrainingPlan trainingPlan = safeDraft.trainingPlan();
        return new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary(
                summary.fitAssessment(), summary.actionRecommendation(), summary.overallRisk()
            ),
            new StructuredInterviewReport.ReportScores(technical, expression, logic, overall),
            stagePerformances,
            questionReviews,
            safeList(safeDraft.strengths()),
            formatWeaknesses(weaknesses),
            new StructuredInterviewReport.TrainingPlan(
                safeList(trainingPlan.threeDay()),
                safeList(trainingPlan.sevenDay()),
                safeList(trainingPlan.nextInterviewFocus())
            ),
            safeDraft.finalAdvice(),
            safeDraft.reportMarkdown()
        );
    }

    private List<StructuredInterviewReport.QuestionReview> buildQuestionReviews(
        List<InterviewStage> stages,
        List<InterviewMessage> messages,
        Map<String, InterviewReportDraft.StageNarrative> narratives
    ) {
        List<StructuredInterviewReport.QuestionReview> reviews = new ArrayList<>();
        String pendingQuestion = null;
        for (InterviewMessage message : messages) {
            if ("assistant".equals(message.getRole()) && !isBlank(message.getContent())) {
                pendingQuestion = message.getContent().trim();
                continue;
            }
            if (!"user".equals(message.getRole())) {
                continue;
            }
            String stageName = resolveStageName(stages, message.getCreatedAt());
            InterviewReportDraft.StageNarrative narrative = narratives.get(stageName);
            reviews.add(new StructuredInterviewReport.QuestionReview(
                stageName,
                pendingQuestion == null ? "语音或上下文追问" : pendingQuestion,
                summarize(message.getContent()),
                message.getScore(),
                text(message.getHint(), "暂无评分依据"),
                improvementSuggestion(narrative)
            ));
            pendingQuestion = null;
        }
        return reviews;
    }

    private StructuredInterviewReport.StagePerformance buildStagePerformance(
        String stageName,
        InterviewReportDraft.StageNarrative narrative,
        List<StructuredInterviewReport.QuestionReview> reviews
    ) {
        List<Integer> scores = reviews.stream()
            .filter(review -> stageName.equals(review.stageName()))
            .map(StructuredInterviewReport.QuestionReview::score)
            .filter(Objects::nonNull)
            .toList();
        Double average = scores.isEmpty()
            ? null
            : oneDecimal(scores.stream().mapToInt(Integer::intValue).average().orElse(0));

        return new StructuredInterviewReport.StagePerformance(
            stageName,
            average,
            narrative == null ? "本阶段暂无补充总结" : narrative.summary(),
            narrative == null ? List.of() : safeList(narrative.positiveSignals()),
            narrative == null ? List.of() : safeList(narrative.negativeSignals()),
            narrative == null ? List.of() : safeList(narrative.improvementSuggestions())
        );
    }

    private String resolveStageName(List<InterviewStage> stages, LocalDateTime createdAt) {
        if (createdAt != null) {
            for (InterviewStage stage : stages) {
                LocalDateTime start = stage.getStartedAt();
                LocalDateTime end = stage.getEndedAt();
                if (start != null && !createdAt.isBefore(start) && (end == null || createdAt.isBefore(end))) {
                    return normalizeStage(stage.getStageName());
                }
            }
        }
        if (!stages.isEmpty()) {
            return normalizeStage(stages.getFirst().getStageName());
        }
        return "warmup";
    }

    private String normalizeStage(String stageName) {
        return STAGE_ORDER.contains(stageName) ? stageName : "warmup";
    }

    private String improvementSuggestion(InterviewReportDraft.StageNarrative narrative) {
        if (narrative != null && !safeList(narrative.improvementSuggestions()).isEmpty()) {
            return narrative.improvementSuggestions().getFirst();
        }
        return "结合评分依据补充具体场景、取舍和量化结果。";
    }

    private List<String> formatWeaknesses(List<UserWeakness> weaknesses) {
        return safeList(weaknesses).stream()
            .filter(Objects::nonNull)
            .filter(item -> !isBlank(item.getCategory()) || !isBlank(item.getDescription()))
            .map(item -> {
                String category = text(item.getCategory(), "待提升项");
                String description = text(item.getDescription(), "需要继续专项训练");
                return category + "：" + description;
            })
            .toList();
    }

    private String summarize(String content) {
        String value = text(content, "未记录有效回答");
        if (value.length() <= ANSWER_SUMMARY_LIMIT) {
            return value;
        }
        return value.substring(0, ANSWER_SUMMARY_LIMIT) + "…";
    }

    private String text(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
