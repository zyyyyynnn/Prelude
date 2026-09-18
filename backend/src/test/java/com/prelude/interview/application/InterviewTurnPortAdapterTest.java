package com.prelude.interview.application;

import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewTurnPortAdapterTest {

    private final RunInterviewTurn runInterviewTurn = mock(RunInterviewTurn.class);
    private final InterviewJudgeService interviewJudgeService = mock(InterviewJudgeService.class);
    private final InterviewSummaryService interviewSummaryService = mock(InterviewSummaryService.class);
    private final InterviewTurnPortAdapter port = new InterviewTurnPortAdapter(
        runInterviewTurn, interviewJudgeService, interviewSummaryService);

    @Test
    void executeDelegatesToRunInterviewTurn() {
        var command = SessionFixtures.turnCommand(1L, 2L, "hi", false, false);
        var sink = SessionFixtures.noopSink();
        var expected = SessionFixtures.turnResult(SessionFixtures.create(1L), SessionFixtures.message(), "ok");
        when(runInterviewTurn.execute(any(), any())).thenReturn(expected);

        assertThat(port.execute(command, sink)).isSameAs(expected);
        verify(runInterviewTurn).execute(same(command), same(sink));
    }

    @Test
    void judgeAndPersistMapsScoreAndHint() {
        var session = SessionFixtures.create(1L);
        var message = SessionFixtures.message();
        when(interviewJudgeService.judgeAndPersist(session, message))
            .thenReturn(Optional.of(SessionFixtures.judgeResult(8, "hint", "{\"score\":8}")));

        var outcome = port.judgeAndPersist(session, message);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().score()).isEqualTo(8);
        assertThat(outcome.get().hint()).isEqualTo("hint");
        assertThat(outcome.get().json()).isEqualTo("{\"score\":8}");
    }

    @Test
    void summarizeIfNeededDelegates() {
        var session = SessionFixtures.create(1L);
        port.summarizeIfNeeded(session);
        verify(interviewSummaryService).triggerAsyncSummarizeIfNeeded(session);
    }
}
