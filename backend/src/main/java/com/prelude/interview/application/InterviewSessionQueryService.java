package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.llm.api.LlmPort;
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
    private final AttachmentContextPort attachmentContextPort;
    private final LlmPort llmPort;

    public List<InterviewSessionSummary> listCurrentUserSessions() {
        return interviewSessionRepository.listByUser(sessionAccess.currentAccountId())
            .stream()
            .map(session -> interviewResponseAssembler.toSessionItem(
                session, interviewStageManager.currentStageName(session.getId())))
            .toList();
    }

    public InterviewSessionDetails getSessionMessages(Long sessionId) {
        InterviewSession session = sessionAccess.requireOwned(sessionId, sessionAccess.currentAccountId());
        List<InterviewStage> stages = interviewStageManager.listStages(sessionId);
        List<InterviewMessage> messages = interviewMessageRepository.listBySession(sessionId);
        LlmPort.FrozenModelConfiguration modelConfiguration = llmPort.frozenConfiguration(
            session.getAccountId(), session.getModelExecutionSnapshotId());
        List<InterviewAttachmentView> attachments = attachmentContextPort
            .list(session.getAccountId(), "interview", sessionId)
            .stream()
            .map(item -> new InterviewAttachmentView(
                item.id(), item.fileName(), item.mediaType(), item.size(), item.image()))
            .toList();
        return interviewResponseAssembler.toMessagesResponse(
            session, stages, messages, modelConfiguration, attachments);
    }
}
