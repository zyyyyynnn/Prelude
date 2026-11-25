package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateInterviewStageTest {

    @Test
    void locksAndInitializesTheSessionBeforeMovingWithoutAPreviousStageRead() {
        InterviewSessionAccess access = mock(InterviewSessionAccess.class);
        InterviewStageManager stages = mock(InterviewStageManager.class);
        InterviewSession session = SessionFixtures.create(42L, 7L, "ongoing");
        InterviewStage technical = SessionFixtures.stage(42L, "technical");
        technical.setStartedAt(LocalDateTime.of(2026, 11, 25, 12, 0));
        when(access.currentAccountId()).thenReturn(7L);
        when(access.requireOngoing(42L, 7L)).thenReturn(session);
        when(stages.moveToStage(42L, "technical")).thenReturn(technical);
        UpdateInterviewStage update = new UpdateInterviewStage(access, stages);

        UpdateInterviewStageResult result = update.execute(42L, "technical");

        assertThat(result.stageName()).isEqualTo("technical");
        InOrder order = inOrder(stages);
        order.verify(stages).ensureInitialStage(session);
        order.verify(stages).moveToStage(42L, "technical");
        verify(stages, never()).currentOrLatestStage(42L);
    }
}
