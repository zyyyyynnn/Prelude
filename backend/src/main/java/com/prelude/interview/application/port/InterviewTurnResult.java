package com.prelude.interview.application.port;

public record InterviewTurnResult(
    InterviewTurnSessionSnapshot session,
    InterviewUserTurnSnapshot userTurn,
    String assistantReply
) {
}
