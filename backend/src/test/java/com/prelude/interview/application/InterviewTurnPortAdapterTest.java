package com.prelude.interview.application;

import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewTurnPortAdapterTest {

    private final RunInterviewTurn runInterviewTurn = mock(RunInterviewTurn.class);
    private final InterviewJudgeService interviewJudgeService = mock(InterviewJudgeService.class);
    private final InterviewSummaryService interviewSummaryService = mock(InterviewSummaryService.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final InterviewMessageRepository messageRepository = mock(InterviewMessageRepository.class);
    private final InterviewTurnPortAdapter port = new InterviewTurnPortAdapter(
        runInterviewTurn, interviewJudgeService, interviewSummaryService,
        sessionRepository, messageRepository);

    @Test
    void executeDelegatesToRunInterviewTurn() {
        var command = SessionFixtures.turnCommand(1L, 2L, "hi", false, false);
        var sink = SessionFixtures.noopSink();
        var expected = SessionFixtures.turnResult(SessionFixtures.turnSession(1L), SessionFixtures.userTurn(9L, 1L, "hi"), "ok");
        when(runInterviewTurn.execute(any(), any())).thenReturn(expected);

        assertThat(port.execute(command, sink)).isSameAs(expected);
        verify(runInterviewTurn).execute(same(command), same(sink));
    }

    @Test
    void judgeAndPersistReloadsTheDomainFromTheIdentifiers() {
        InterviewSession session = SessionFixtures.create(7L);
        InterviewMessage message = SessionFixtures.message();
        when(sessionRepository.selectById(7L)).thenReturn(session);
        when(messageRepository.findById(11L)).thenReturn(message);
        when(interviewJudgeService.judgeAndPersist(session, message))
            .thenReturn(Optional.of(SessionFixtures.judgeResult(8, "hint", "{\"score\":8}")));

        var outcome = port.judgeAndPersist(7L, 11L);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().score()).isEqualTo(8);
        assertThat(outcome.get().hint()).isEqualTo("hint");
        assertThat(outcome.get().json()).isEqualTo("{\"score\":8}");
    }

    @Test
    void judgeAndPersistSkipsWhenEitherRowIsGone() {
        when(sessionRepository.selectById(7L)).thenReturn(null);

        assertThat(port.judgeAndPersist(7L, 11L)).isEmpty();
        verifyNoInteractions(interviewJudgeService);
    }

    @Test
    void summarizeIfNeededReloadsTheSession() {
        InterviewSession session = SessionFixtures.create(1L);
        when(sessionRepository.selectById(1L)).thenReturn(session);

        port.summarizeIfNeeded(1L);

        verify(interviewSummaryService).triggerAsyncSummarizeIfNeeded(session);
    }
}
