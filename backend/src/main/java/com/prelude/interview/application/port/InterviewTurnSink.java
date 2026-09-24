package com.prelude.interview.application.port;

@FunctionalInterface
public interface InterviewTurnSink {

    default void userAccepted(InterviewUserTurnSnapshot userTurn) {
    }

    void assistantDelta(String delta);
}
