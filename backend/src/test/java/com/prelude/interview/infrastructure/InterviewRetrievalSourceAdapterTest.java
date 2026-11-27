package com.prelude.interview.infrastructure;

import com.prelude.assets.api.AssetRef;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewRetrievalSourceAdapterTest {

    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final ResumeContextPort resumeContextPort = mock(ResumeContextPort.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final InterviewRetrievalSourceAdapter adapter = new InterviewRetrievalSourceAdapter(
        sessionRepository, resumeContextPort, attachmentContextPort);

    @Test
    void rebuildIncludesResumeJdAndAttachmentTextThroughTheAttachmentPort() {
        InterviewSession session = session();
        when(sessionRepository.selectById(42L)).thenReturn(session);
        when(resumeContextPort.requireOwnedProjection(7L, 3L)).thenReturn(new ResumeProjection(
            3L, 7L, "resume.txt", "resume text", List.of(), List.of()));
        when(attachmentContextPort.list(7L, "interview", 42L)).thenReturn(List.of(
            new AttachmentSnapshot(8L, "notes.txt", "text/plain", 5L, false, "attachment text", new AssetRef(100L)),
            new AttachmentSnapshot(9L, "diagram.png", "image/png", 5L, true, "image text", new AssetRef(101L)),
            new AttachmentSnapshot(10L, "empty.txt", "text/plain", 0L, false, " ", new AssetRef(102L))
        ));

        assertThat(adapter.loadDocuments(RetrievalPort.SCOPE_SESSION, 42L))
            .containsExactly("resume text", "jd text", "attachment text", "image text");
        verify(attachmentContextPort).list(7L, "interview", 42L);
    }

    @Test
    void missingSessionDoesNotReadResumeOrAttachments() {
        assertThat(adapter.loadDocuments(RetrievalPort.SCOPE_SESSION, 42L)).isEmpty();

        verifyNoInteractions(resumeContextPort, attachmentContextPort);
    }

    @Test
    void unsupportedScopeDoesNotReadInterviewSources() {
        assertThat(adapter.loadDocuments("other", 42L)).isEmpty();

        verifyNoInteractions(sessionRepository, resumeContextPort, attachmentContextPort);
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(42L);
        session.setAccountId(7L);
        session.setResumeId(3L);
        session.setJdText("jd text");
        return session;
    }
}
