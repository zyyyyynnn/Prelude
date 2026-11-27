package com.prelude.interview.infrastructure;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.interview.application.InterviewRetrievalDocuments;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.context.RetrievalPort;
import com.prelude.context.RetrievalSourcePort;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InterviewRetrievalSourceAdapter implements RetrievalSourcePort {

    private final InterviewSessionRepository interviewSessionRepository;
    private final ResumeContextPort resumeContextPort;
    private final AttachmentContextPort attachmentContextPort;

    @Override
    public List<String> loadDocuments(String scopeType, Long scopeId) {
        if (!RetrievalPort.SCOPE_SESSION.equals(scopeType)) {
            return List.of();
        }
        InterviewSession session = interviewSessionRepository.selectById(scopeId);
        if (session == null) {
            return List.of();
        }
        ResumeProjection resume = resumeContextPort.requireOwnedProjection(
            session.getAccountId(),
            session.getResumeId()
        );
        return InterviewRetrievalDocuments.from(
            resume,
            session.getJdText(),
            attachmentContextPort.list(session.getAccountId(), "interview", session.getId())
        );
    }

}
