package com.interview.service.impl;

import com.interview.dto.InterviewReportDraft;
import com.interview.dto.StructuredInterviewReport;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewStage;
import com.interview.entity.UserWeakness;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewReportAssemblerTest {

    private final InterviewReportAssembler assembler = new InterviewReportAssembler();

    @Test
    void assemblesPersistedQuestionScoresStageAveragesAndWeaknesses() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 4, 10, 0);
        List<InterviewStage> stages = List.of(
            stage("warmup", startedAt, startedAt.plusMinutes(10)),
            stage("technical", startedAt.plusMinutes(10), startedAt.plusMinutes(20)),
            stage("deep_dive", startedAt.plusMinutes(20), startedAt.plusMinutes(30)),
            stage("closing", startedAt.plusMinutes(30), startedAt.plusMinutes(40))
        );
        List<InterviewMessage> messages = List.of(
            message("assistant", "请介绍项目背景", null, null, 1, startedAt.plusMinutes(1)),
            message("user", "我负责核心链路。", 8, "补充业务指标", 2, startedAt.plusMinutes(2)),
            message("assistant", "如何保证接口幂等？", null, null, 3, startedAt.plusMinutes(11)),
            message("user", "使用唯一请求键。", 6, "说明冲突处理", 4, startedAt.plusMinutes(12)),
            message("assistant", "如何处理重复消息？", null, null, 5, startedAt.plusMinutes(13)),
            message("user", "消费前检查业务键。", 8, "补充过期策略", 6, startedAt.plusMinutes(14))
        );
        UserWeakness weakness = new UserWeakness();
        weakness.setCategory("性能量化");
        weakness.setDescription("缺少压测指标和容量边界。");

        StructuredInterviewReport report = assembler.assemble(
            draft(), stages, messages, List.of(weakness)
        );

        assertThat(report.scores().technical()).isEqualTo(8);
        assertThat(report.scores().expression()).isEqualTo(7);
        assertThat(report.scores().logic()).isEqualTo(9);
        assertThat(report.scores().overall()).isEqualTo(8.0);
        assertThat(report.questionReviews()).hasSize(3);
        assertThat(report.questionReviews().get(0)).satisfies(review -> {
            assertThat(review.question()).isEqualTo("请介绍项目背景");
            assertThat(review.answerSummary()).isEqualTo("我负责核心链路。");
            assertThat(review.score()).isEqualTo(8);
            assertThat(review.scoringReason()).isEqualTo("补充业务指标");
            assertThat(review.stageName()).isEqualTo("warmup");
        });
        assertThat(report.stagePerformances()).extracting(StructuredInterviewReport.StagePerformance::stageName)
            .containsExactly("warmup", "technical", "deep_dive", "closing");
        assertThat(report.stagePerformances().get(0).score()).isEqualTo(8.0);
        assertThat(report.stagePerformances().get(1).score()).isEqualTo(7.0);
        assertThat(report.stagePerformances().get(2).score()).isNull();
        assertThat(report.weaknesses()).containsExactly("性能量化：缺少压测指标和容量边界。");
    }

    @Test
    void keepsEveryRealQuestionWhenNarrativeStagesAreMissing() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 4, 10, 0);
        List<InterviewMessage> messages = List.of(
            message("assistant", "问题一", null, null, 1, startedAt.plusMinutes(1)),
            message("user", "回答一", null, null, 2, startedAt.plusMinutes(2)),
            message("assistant", "问题二", null, null, 3, startedAt.plusMinutes(3)),
            message("user", "回答二", 7, "回答已记录", 4, startedAt.plusMinutes(4))
        );

        StructuredInterviewReport report = assembler.assemble(
            draftWithoutStages(),
            List.of(stage("warmup", startedAt, startedAt.plusMinutes(10))),
            messages,
            List.of()
        );

        assertThat(report.questionReviews()).hasSize(2);
        assertThat(report.questionReviews().get(0).score()).isNull();
        assertThat(report.questionReviews().get(0).scoringReason()).isEqualTo("暂无评分依据");
        assertThat(report.questionReviews().get(1).score()).isEqualTo(7);
    }

    @Test
    void assignsBoundaryMessageToStageStartingAtThatTimestamp() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 4, 10, 0);
        List<InterviewStage> stages = List.of(
            stage("warmup", startedAt, startedAt.plusMinutes(10)),
            stage("technical", startedAt.plusMinutes(10), startedAt.plusMinutes(20))
        );

        StructuredInterviewReport report = assembler.assemble(
            draft(),
            stages,
            List.of(
                message("assistant", "边界问题", null, null, 1, startedAt.plusMinutes(10)),
                message("user", "边界回答", 9, "完整", 2, startedAt.plusMinutes(10))
            ),
            List.of()
        );

        assertThat(report.questionReviews()).singleElement()
            .extracting(StructuredInterviewReport.QuestionReview::stageName)
            .isEqualTo("technical");
    }

    private InterviewReportDraft draft() {
        return new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("中等偏高", "继续投递", "量化不足"),
            new InterviewReportDraft.DimensionScores(8, 7, 9),
            List.of(new InterviewReportDraft.StageNarrative(
                "technical", "技术基础稳定", List.of("结构清楚"), List.of("缺少指标"), List.of("补充量化依据")
            )),
            List.of("表达结构清晰"),
            new InterviewReportDraft.TrainingPlan(List.of("复盘"), List.of("专项训练"), List.of("量化表达")),
            "保持训练",
            "# 报告"
        );
    }

    private InterviewReportDraft draftWithoutStages() {
        InterviewReportDraft source = draft();
        return new InterviewReportDraft(
            source.summary(), source.scores(), List.of(), source.strengths(), source.trainingPlan(),
            source.finalAdvice(), source.reportMarkdown()
        );
    }

    private InterviewStage stage(String name, LocalDateTime startedAt, LocalDateTime endedAt) {
        InterviewStage stage = new InterviewStage();
        stage.setStageName(name);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        return stage;
    }

    private InterviewMessage message(
        String role,
        String content,
        Integer score,
        String hint,
        int seqNum,
        LocalDateTime createdAt
    ) {
        InterviewMessage message = new InterviewMessage();
        message.setRole(role);
        message.setContent(content);
        message.setScore(score);
        message.setHint(hint);
        message.setSeqNum(seqNum);
        message.setCreatedAt(createdAt);
        return message;
    }
}
