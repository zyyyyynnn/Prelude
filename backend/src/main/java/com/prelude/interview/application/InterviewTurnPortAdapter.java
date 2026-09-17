package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class InterviewTurnPortAdapter implements InterviewTurnPort {

    private final RunInterviewTurn runInterviewTurn;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;

    @Override
    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        return runInterviewTurn.execute(command, sink);
    }

    @Override
    public Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMessage) {
        return interviewJudgeService.judgeAndPersist(session, userMessage);
    }

    @Override
    public void summarizeIfNeeded(InterviewSession session) {
        interviewSummaryService.triggerAsyncSummarizeIfNeeded(session);
    }
}
