package com.interview.interview.application;

import com.interview.interview.api.InterviewMessagesResponse;
import com.interview.interview.api.InterviewSessionItemResponse;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewSessionQueryService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewStageManager interviewStageManager;
    private final InterviewResponseAssembler interviewResponseAssembler;
    private final InterviewSessionAccess sessionAccess;

    public List<InterviewSessionItemResponse> listCurrentUserSessions() {
        return interviewSessionRepository.listByUser(sessionAccess.currentUserId())
            .stream()
            .map(session -> interviewResponseAssembler.toSessionItem(
                session, interviewStageManager.currentStageName(session.getId())))
            .toList();
    }

    public InterviewMessagesResponse getSessionMessages(Long sessionId) {
        InterviewSession session = sessionAccess.requireOwned(sessionId, sessionAccess.currentUserId());
        List<InterviewStage> stages = interviewStageManager.listStages(sessionId);
        List<InterviewMessage> messages = interviewMessageRepository.listBySession(sessionId);
        return interviewResponseAssembler.toMessagesResponse(session, stages, messages);
    }
}
