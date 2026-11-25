package com.prelude.interview.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewTurnCompletion {

    private static final String ROLE_ASSISTANT = "assistant";

    private final InterviewMessageService interviewMessageService;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long sessionId, String reply) {
        if (reply == null || reply.isBlank()) {
            return;
        }
        interviewMessageService.insertMessage(sessionId, ROLE_ASSISTANT, reply);
        interviewStageManager.advanceIfReady(sessionId);
    }
}
