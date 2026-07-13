package com.interview.interview.application;

import com.interview.interview.api.InterviewStageUpdateRequest;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateInterviewStageTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewStageManager stageManager = mock(InterviewStageManager.class);
    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private final InterviewFixturePort devFixtureService = mock(InterviewFixturePort.class);

    @Test
    void initializesStageMovesForwardAndAddsFixturePrompt() {
        InterviewSession session = session(7L);
        InterviewStage moved = stage("technical");
        when(sessionAccess.currentUserId()).thenReturn(42L);
        when(sessionAccess.requireOngoing(7L, 42L)).thenReturn(session);
        when(stageManager.currentOrLatestStage(7L)).thenReturn(null);
        when(stageManager.moveToStage(7L, "technical", true)).thenReturn(moved);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(devFixtureService.resolveScriptedReply("technical", 0)).thenReturn("技术问题");

        var response = useCase().execute(7L, new InterviewStageUpdateRequest("technical"));

        assertThat(response.stageName()).isEqualTo("technical");
        verify(stageManager).ensureInitialStage(session);
        verify(messageService).insertMessage(7L, "assistant", "技术问题");
    }

    @Test
    void normalModeDoesNotInsertFixturePrompt() {
        when(sessionAccess.currentUserId()).thenReturn(42L);
        when(sessionAccess.requireOngoing(7L, 42L)).thenReturn(session(7L));
        when(stageManager.currentOrLatestStage(7L)).thenReturn(stage("warmup"));
        when(stageManager.moveToStage(7L, "technical", true)).thenReturn(stage("technical"));
        when(devFixtureService.isEnabled()).thenReturn(false);

        useCase().execute(7L, new InterviewStageUpdateRequest("technical"));

        verify(messageService, never()).insertMessage(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private UpdateInterviewStage useCase() {
        return new UpdateInterviewStage(sessionAccess, stageManager, messageService, devFixtureService);
    }

    private InterviewSession session(Long id) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(42L);
        session.setStatus("ongoing");
        return session;
    }

    private InterviewStage stage(String name) {
        InterviewStage stage = new InterviewStage();
        stage.setStageName(name);
        stage.setStartedAt(LocalDateTime.of(2026, 7, 12, 10, 0));
        return stage;
    }
}
