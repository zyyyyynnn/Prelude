package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapts the cross-module turn port to the interview services. The port speaks in
 * identifiers; this adapter reloads the domain objects, so the row and domain shapes
 * never leave the module.
 */
@Service
@RequiredArgsConstructor
class InterviewTurnPortAdapter implements InterviewTurnPort {

    private final RunInterviewTurn runInterviewTurn;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;

    @Override
    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        return runInterviewTurn.execute(command, sink);
    }

    @Override
    public Optional<JudgeResult> judgeAndPersist(Long sessionId, Long messageId) {
        InterviewSession session = sessionRepository.selectById(sessionId);
        InterviewMessage userMessage = messageRepository.findById(messageId);
        if (session == null || userMessage == null) {
            return Optional.empty();
        }
        return interviewJudgeService.judgeAndPersist(session, userMessage);
    }

    @Override
    public void summarizeIfNeeded(Long sessionId) {
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session != null) {
            interviewSummaryService.triggerAsyncSummarizeIfNeeded(session);
        }
    }
}
