package com.prelude.test;

import com.prelude.artifact.application.GenerateInterviewReport;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.artifact.domain.InterviewReportAssembler;
import com.prelude.artifact.domain.InterviewReportDraft;
import com.prelude.artifact.domain.ReportParser;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.StructuredInterviewReport;
import org.mockito.Mockito;

import java.util.List;

public final class ArtifactFixtures {

    private ArtifactFixtures() {
    }

    public static GenerateInterviewReport.GenerationResult generationResult(long accountId, long sessionId) {
        ScoreHistory score = new ScoreHistory();
        score.setAccountId(accountId);
        score.setSessionId(sessionId);
        score.setTechnicalScore(8);
        score.setExpressionScore(7);
        score.setLogicScore(9);

        AccountWeakness weakness = new AccountWeakness();
        weakness.setAccountId(accountId);
        weakness.setSessionId(sessionId);
        weakness.setCategory("system-design");
        weakness.setDescription("needs stronger capacity evidence");

        return new GenerateInterviewReport.GenerationResult(
            GenerateInterviewReport.Outcome.GENERATED,
            "{\"report\":\"ready\"}",
            score,
            List.of(weakness)
        );
    }

    public static InterviewReportDraft sampleDraft() {
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

    public static StructuredInterviewReport sampleReport() {
        return new StructuredInterviewReport(
            new StructuredInterviewReport.ReportSummary("fit", "act", "risk"),
            new StructuredInterviewReport.ReportScores(8, 8, 8, 8.0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new StructuredInterviewReport.TrainingPlan(List.of(), List.of(), List.of()),
            "advice"
        );
    }

    public static ReportParser mockParser() {
        ReportParser parser = Mockito.mock(ReportParser.class);
        Mockito.when(parser.parseDraft(Mockito.anyString())).thenReturn(sampleDraft());
        return parser;
    }

    public static InterviewReportAssembler mockAssembler() {
        InterviewReportAssembler assembler = Mockito.mock(InterviewReportAssembler.class);
        Mockito.when(assembler.assemble(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(sampleReport());
        return assembler;
    }
}