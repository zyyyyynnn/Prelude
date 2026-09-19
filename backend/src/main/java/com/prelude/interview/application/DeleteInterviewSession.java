package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently removes one interview session. Messages, stages, scores and
 * weakness rows follow through database cascades; retrieval chunks and
 * attachment bindings are session-scoped without foreign keys, so they are
 * released explicitly inside the same transaction.
 */
@Service
@RequiredArgsConstructor
public class DeleteInterviewSession {

    private static final String ATTACHMENT_SCOPE = "interview";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final RetrievalPort retrievalPort;
    private final AttachmentContextPort attachmentContextPort;

    @Transactional
    public void execute(Long sessionId) {
        Long accountId = sessionAccess.currentAccountId();
        sessionAccess.requireOwned(sessionId, accountId);

        interviewSessionRepository.deleteOwned(sessionId, accountId);
        retrievalPort.invalidate(RetrievalPort.SCOPE_SESSION, sessionId);
        attachmentContextPort.unbind(accountId, ATTACHMENT_SCOPE, sessionId);
    }
}
