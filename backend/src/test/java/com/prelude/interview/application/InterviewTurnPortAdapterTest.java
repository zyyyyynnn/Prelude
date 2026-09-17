package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
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
    private final InterviewTurnPort port = new InterviewTurnPortAdapter(
        runInterviewTurn, interviewJudgeService, interviewSummaryService);

    @Test
    void executeDelegatesToRunInterviewTurn() {
        InterviewTurnCommand command = new InterviewTurnCommand(1L, 2L, "hi", false, false);
        InterviewTurnSink sink = delta -> {
        };
        InterviewTurnResult expected = new InterviewTurnResult(new InterviewSession(), new InterviewMessage(), "ok");
        when(runInterviewTurn.execute(any(), any())).thenReturn(expected);

        assertThat(port.execute(command, sink)).isSameAs(expected);
        verify(runInterviewTurn).execute(same(command), same(sink));
    }

    @Test
    void judgeAndPersistMapsScoreAndHint() {
        InterviewSession session = new InterviewSession();
        InterviewMessage message = new InterviewMessage();
        when(interviewJudgeService.judgeAndPersist(session, message))
            .thenReturn(Optional.of(new JudgeResult(8, "hint", "{\"score\":8}")));

        Optional<JudgeResult> outcome = port.judgeAndPersist(session, message);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().score()).isEqualTo(8);
        assertThat(outcome.get().hint()).isEqualTo("hint");
        assertThat(outcome.get().json()).isEqualTo("{\"score\":8}");
    }

    @Test
    void summarizeIfNeededDelegates() {
        InterviewSession session = new InterviewSession();
        port.summarizeIfNeeded(session);
        verify(interviewSummaryService).triggerAsyncSummarizeIfNeeded(session);
    }
}
