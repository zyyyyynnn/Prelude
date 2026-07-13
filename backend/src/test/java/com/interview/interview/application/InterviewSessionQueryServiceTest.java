package com.interview.interview.application;

import com.interview.interview.api.InterviewMessagesResponse;
import com.interview.interview.api.InterviewSessionItemResponse;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionQueryServiceTest {

    private final InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
    private final InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
    private final InterviewStageManager stageManager = mock(InterviewStageManager.class);
    private final InterviewResponseAssembler assembler = mock(InterviewResponseAssembler.class);
    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);

    @Test
    void listsCurrentUserSessionsWithCurrentStage() {
        InterviewSession session = session();
        InterviewSessionItemResponse item = new InterviewSessionItemResponse(
            7L, "Java", "ongoing", session.getCreatedAt(), "technical", "openai", "gpt", null
        );
        when(sessionAccess.currentUserId()).thenReturn(42L);
        when(sessionMapper.listByUser(42L)).thenReturn(List.of(session));
        when(stageManager.currentStageName(7L)).thenReturn("technical");
        when(assembler.toSessionItem(session, "technical")).thenReturn(item);

        assertThat(service().listCurrentUserSessions()).containsExactly(item);
    }

    @Test
    void getsOwnedSessionMessagesInPersistedOrder() {
        InterviewSession session = session();
        InterviewStage stage = new InterviewStage();
        InterviewMessage message = new InterviewMessage();
        InterviewMessagesResponse response = new InterviewMessagesResponse(
            7L, "Java", "ongoing", "warmup", null, List.of(), List.of(), 1L, 2L, "JD"
        );
        when(sessionAccess.currentUserId()).thenReturn(42L);
        when(sessionAccess.requireOwned(7L, 42L)).thenReturn(session);
        when(stageManager.listStages(7L)).thenReturn(List.of(stage));
        when(messageMapper.listBySession(7L)).thenReturn(List.of(message));
        when(assembler.toMessagesResponse(session, List.of(stage), List.of(message))).thenReturn(response);

        assertThat(service().getSessionMessages(7L)).isEqualTo(response);
        verify(assembler).toMessagesResponse(session, List.of(stage), List.of(message));
    }

    private InterviewSessionQueryService service() {
        return new InterviewSessionQueryService(
            sessionMapper, messageMapper, stageManager, assembler, sessionAccess
        );
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setCreatedAt(LocalDateTime.of(2026, 7, 12, 10, 0));
        return session;
    }
}
