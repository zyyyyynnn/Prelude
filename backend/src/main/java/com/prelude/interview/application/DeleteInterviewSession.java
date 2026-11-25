package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteInterviewSession {

    private static final String ATTACHMENT_SCOPE = "interview";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final RetrievalPort retrievalPort;
    private final AttachmentContextPort attachmentContextPort;

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long sessionId) {
        Long accountId = sessionAccess.currentAccountId();
        sessionAccess.requireOwned(sessionId, accountId);

        interviewSessionRepository.deleteOwned(sessionId, accountId);
        retrievalPort.invalidate(RetrievalPort.SCOPE_SESSION, sessionId);
        attachmentContextPort.deleteScope(accountId, ATTACHMENT_SCOPE, sessionId);
    }
}
