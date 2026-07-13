package com.interview.interview.application;

import com.interview.interview.domain.InterviewMessage;

@FunctionalInterface
public interface InterviewTurnSink {

    default void userAccepted(InterviewMessage userMessage) {
    }

    void assistantDelta(String delta);
}
