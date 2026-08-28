package com.prelude.interview.application;

import com.prelude.UserContext;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.LlmConfigPort;
import com.prelude.llm.LlmSelection;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import com.prelude.template.api.port.PositionCatalogPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartInterviewTest {

    @AfterEach
    void clearUser() {
        UserContext.remove();
    }

    @Test
    void validatesEveryResourceAgainstTheCurrentOwnerBeforeStarting() {
        ResumeContextPort resumes = mock(ResumeContextPort.class);
        PositionCatalogPort positions = mock(PositionCatalogPort.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        LlmConfigPort llm = mock(LlmConfigPort.class);
        InterviewStageManager stages = mock(InterviewStageManager.class);
        InterviewMessageService messages = mock(InterviewMessageService.class);
        RetrievalPort retrieval = mock(RetrievalPort.class);
        AttachmentContextPort attachments = mock(AttachmentContextPort.class);
        UserContext.setCurrentUserId(7L);
        when(resumes.requireOwnedProjection(7L, 11L))
            .thenReturn(new ResumeProjection(11L, 7L, "candidate.pdf", "resume text", List.of(), List.of(), 1));
        when(attachments.requireOwned(7L, List.of(31L)))
            .thenReturn(List.of(new AttachmentSnapshot(31L, "notes.txt", "text/plain", 5, false, "notes", null)));
        when(positions.findAccessibleById(7L, 21L))
            .thenReturn(new PositionCatalogPort.PositionSnapshot(21L, "Platform Engineer", "system prompt"));
        when(llm.resolveSelection(7L, "gpt-test")).thenReturn(new LlmSelection("custom", "gpt-test"));
        when(llm.currentThinkingDepth()).thenReturn("medium");
        doAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(51L);
            return 1;
        }).when(sessions).add(org.mockito.ArgumentMatchers.any());

        StartInterviewResult result = new StartInterview(
            resumes,
            positions,
            sessions,
            llm,
            stages,
            messages,
            Runnable::run,
            retrieval,
            attachments
        ).execute(new StartInterviewCommand(11L, 21L, "job description", "gpt-test", List.of(31L)));

        assertThat(result.sessionId()).isEqualTo(51L);
        assertThat(result.currentStage()).isEqualTo("warmup");
        ArgumentCaptor<InterviewSession> stored = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessions).add(stored.capture());
        assertThat(stored.getValue().getUserId()).isEqualTo(7L);
        assertThat(stored.getValue().getResumeId()).isEqualTo(11L);
        assertThat(stored.getValue().getStatus()).isEqualTo("ongoing");
        verify(attachments).bind(7L, List.of(31L), "interview", 51L);
        verify(retrieval).index("session", 51L, List.of("resume text", "job description", "notes"));
        verify(messages).insertMessage(51L, "system", "system prompt");
        verify(stages).ensureInitialStage(stored.getValue());
    }
}
