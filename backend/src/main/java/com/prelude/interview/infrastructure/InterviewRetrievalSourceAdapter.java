package com.prelude.interview.infrastructure;

import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.infrastructure.persistence.InterviewSessionMapper;
import com.prelude.context.RetrievalPort;
import com.prelude.context.RetrievalSourcePort;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InterviewRetrievalSourceAdapter implements RetrievalSourcePort {

    private final InterviewSessionMapper interviewSessionMapper;
    private final ResumeContextPort resumeContextPort;

    @Override
    public List<String> loadDocuments(String scopeType, Long scopeId) {
        if (!RetrievalPort.SCOPE_SESSION.equals(scopeType)) {
            return List.of();
        }
        InterviewSession session = interviewSessionMapper.selectById(scopeId);
        if (session == null) {
            return List.of();
        }
        ResumeProjection resume = resumeContextPort.requireOwnedProjection(
            session.getUserId(),
            session.getResumeId()
        );
        List<String> documents = new ArrayList<>();
        addIfPresent(documents, resume.plainText());
        addIfPresent(documents, session.getJdText());
        return documents;
    }

    private void addIfPresent(List<String> documents, String value) {
        if (value != null && !value.isBlank()) {
            documents.add(value);
        }
    }
}
