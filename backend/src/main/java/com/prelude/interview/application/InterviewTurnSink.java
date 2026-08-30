package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewMessage;

@FunctionalInterface
public interface InterviewTurnSink {

    default void userAccepted(InterviewMessage userMessage) {
    }

    void assistantDelta(String delta);
}
