package com.prelude.artifact.application;

import com.prelude.artifact.application.port.InsightRepository;
import com.prelude.artifact.domain.InterviewReportAssembler;
import com.prelude.artifact.domain.ReportParser;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.LlmPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerateInterviewReportIdempotenceTest {

    @Test
    void finishedPersistedReportIsRecognizedWithoutCallingTheModelAgain() {
        InterviewReportPort reportPort = mock(InterviewReportPort.class);
        InsightRepository insightRepository = mock(InsightRepository.class);
        LlmPort llmPort = mock(LlmPort.class);
        ReportParser parser = mock(ReportParser.class);
        InterviewReportAssembler assembler = mock(InterviewReportAssembler.class);
        InterviewSession session = new InterviewSession();
        session.setId(42L);
        session.setAccountId(7L);
        session.setStatus("finished");
        session.setSummaryReport("{\"summary\":{}}");
        when(reportPort.findSession(42L)).thenReturn(session);
        GenerateInterviewReport generate = new GenerateInterviewReport(
            new ObjectMapper(), reportPort, insightRepository, llmPort, parser, assembler);

        assertThat(generate.execute(42L, 7L)).isEqualTo(GenerateInterviewReport.Outcome.SKIPPED);

        verifyNoInteractions(llmPort, insightRepository, parser, assembler);
    }
}
