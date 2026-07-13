package com.interview.interview.application;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;

public record InterviewTurnResult(
    InterviewSession session,
    InterviewMessage userMessage,
    String assistantReply
) {
}
