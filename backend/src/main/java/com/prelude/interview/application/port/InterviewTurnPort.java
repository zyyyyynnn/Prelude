package com.prelude.interview.application.port;

import java.util.Optional;

/**
 * Cross-module interview turn contract. Voice and other adapters depend on
 * this port instead of application services, keeping package depth shallow.
 * Every type it exchanges is a snapshot, so no consumer names an interview
 * domain class or a persistence row.
 */
public interface InterviewTurnPort {

    InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink);

    Optional<JudgeResult> judgeAndPersist(Long sessionId, Long messageId);

    void summarizeIfNeeded(Long sessionId);
}
