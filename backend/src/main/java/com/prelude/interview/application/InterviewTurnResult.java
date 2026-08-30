package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;

public record InterviewTurnResult(
    InterviewSession session,
    InterviewMessage userMessage,
    String assistantReply
) {
}
