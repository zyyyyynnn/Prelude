package com.prelude.artifact.domain;

import com.prelude.interview.api.port.InterviewMessageSnapshot;
import com.prelude.interview.api.port.InterviewStageSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewReportAssemblerStageContractTest {

    private final InterviewReportAssembler assembler = new InterviewReportAssembler();

    @Test
    void assignsAnswersByPersistedBoundaryRatherThanTimestamps() {
        LocalDateTime shared = LocalDateTime.of(2026, 11, 27, 12, 0);
        List<InterviewStageSnapshot> stages = List.of(
            stage(1L, "warmup", 0, shared),
            stage(2L, "technical", 3, shared)
        );
        List<InterviewMessageSnapshot> messages = List.of(
            message(1L, "assistant", "warmup question", shared),
            message(2L, "user", "warmup answer", shared),
            message(3L, "assistant", "technical question", shared),
            message(4L, "user", "technical answer", shared)
        );

        StructuredInterviewReport report = assembler.assemble(draft(), stages, messages, List.of());

        assertThat(report.questionReviews()).extracting(StructuredInterviewReport.QuestionReview::stageName)
            .containsExactly("warmup", "technical");
    }

    @Test
    void rejectsUnknownModelNarrativesWithoutInventingAWarmupStage() {
        InterviewReportDraft draft = new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("fit", "act", "risk"),
            new InterviewReportDraft.DimensionScores(8, 8, 8),
            List.of(new InterviewReportDraft.StageNarrative("unknown", "ignored", List.of(), List.of(), List.of())),
            List.of(),
            new InterviewReportDraft.TrainingPlan(List.of(), List.of(), List.of()),
            "advice",
            "report"
        );

        assertThatThrownBy(() -> assembler.assemble(
            draft, List.of(stage(1L, "closing", 0, LocalDateTime.now())), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown interview stage");
    }

    @Test
    void rejectsUnknownPersistedStagesAndAnswersOutsideEveryBoundary() {
        assertThatThrownBy(() -> assembler.assemble(
            draft(), List.of(stage(1L, "unknown", 0, LocalDateTime.now())), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown interview stage");
        assertThatThrownBy(() -> assembler.assemble(
            draft(), List.of(stage(1L, "warmup", 5, LocalDateTime.now())),
            List.of(message(1L, "user", "answer", LocalDateTime.now())), List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No interview stage contains message sequence");
    }

    private InterviewReportDraft draft() {
        return new InterviewReportDraft(
            new InterviewReportDraft.ReportSummary("fit", "act", "risk"),
            new InterviewReportDraft.DimensionScores(8, 8, 8),
            List.of(),
            List.of(),
            new InterviewReportDraft.TrainingPlan(List.of(), List.of(), List.of()),
            "advice",
            "report"
        );
    }

    private InterviewStageSnapshot stage(Long id, String name, int boundarySeq, LocalDateTime startedAt) {
        return new InterviewStageSnapshot(id, 7L, name, boundarySeq, startedAt, null);
    }

    private InterviewMessageSnapshot message(
        Long id, String role, String content, LocalDateTime createdAt
    ) {
        return new InterviewMessageSnapshot(id, 7L, role, content, id.intValue(), null, null, null, null, createdAt);
    }
}
