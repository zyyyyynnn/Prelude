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
        if (reply == null || reply.isBlank()) {
            return;
        }
        interviewMessageService.insertAssistantMessage(sessionId, reply);
        interviewStageManager.advanceIfReady(sessionId);
    }
}
