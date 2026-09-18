package com.prelude.interview.application.port;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;

import java.util.Optional;

/**
 * Cross-module interview turn contract. Voice and other adapters depend on
 * this port instead of application services, keeping package depth shallow.
 */
public interface InterviewTurnPort {

    InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink);

    Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMessage);

    void summarizeIfNeeded(InterviewSession session);
}
