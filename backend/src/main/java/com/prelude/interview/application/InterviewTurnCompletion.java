package com.prelude.interview.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewTurnCompletion {

    private final InterviewMessageService interviewMessageService;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long sessionId, String reply) {
        complete(sessionId, null, reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long sessionId, Long userMessageId, String reply) {
        if (reply == null || reply.isBlank()) {
            return;
        }
        if (interviewMessageService.insertAssistantMessage(sessionId, reply, userMessageId) == null) {
            return;
        }
        interviewStageManager.advanceIfReady(sessionId);
    }
}
