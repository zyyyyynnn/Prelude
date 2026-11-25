package com.prelude.interview.application;

import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import com.prelude.test.SessionFixtures.StageManagerHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewStageManagerTest {

    private static final Long SESSION_ID = 42L;

    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private StageManagerHarness harness;

    @BeforeEach
    void setUp() {
        harness = new StageManagerHarness(messageService, SESSION_ID);
    }

    @Test
    void ensureInitialStageBootstrapsWarmupOnlyOnce() {
        harness.stageManager.ensureInitialStage(SessionFixtures.create(SESSION_ID));
        harness.stageManager.ensureInitialStage(SessionFixtures.create(SESSION_ID));

        assertThat(harness.stages).hasSize(1);
        assertThat(harness.stages.get(0).getStageName()).isEqualTo("warmup");
        assertThat(harness.stages.get(0).getEndedAt()).isNull();
    }

    @Test
    void moveToSameStageIsANoOp() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        var result = harness.stageManager.moveToStage(SESSION_ID, "warmup");

        assertThat(result.getStageName()).isEqualTo("warmup");
        assertThat(harness.stages).hasSize(1);
        verify(messageService, never()).insertMessage(any(), anyString(), anyString());
    }

    @Test
    void moveToStageAdvancesAndInsertsThePolicyPrompt() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        var advanced = harness.stageManager.moveToStage(SESSION_ID, "technical");

        assertThat(advanced.getStageName()).isEqualTo("technical");
        assertThat(harness.stages).hasSize(2);
        assertThat(harness.stages.get(0).getEndedAt()).isNotNull();
        assertThat(harness.stages.get(1).getEndedAt()).isNull();
        verify(messageService).insertMessage(
            eq(SESSION_ID), eq("system"),
            eq("面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。"));
    }

    @Test
    void manualMoveLocksTheSessionBeforeReadingAndChangingStages() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        harness.stageManager.moveToStage(SESSION_ID, "technical");

        InOrder order = inOrder(harness.sessionRepository, harness.stageRepository);
        order.verify(harness.sessionRepository).lockAppendOrder(SESSION_ID);
        order.verify(harness.stageRepository).findCurrentForUpdate(SESSION_ID);
        order.verify(harness.stageRepository).update(any());
        order.verify(harness.stageRepository).add(any());
    }

    @Test
    void automaticAdvanceUsesTheSameSessionThenStageLockOrder() {
        harness.stages.add(harness.stage(SESSION_ID, "technical"));
        harness.messages.add(harness.systemMessage(SESSION_ID, 1, "technical"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 2));
        harness.messages.add(harness.userMessage(SESSION_ID, 3));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 4));

        harness.stageManager.advanceIfReady(SESSION_ID);

        InOrder order = inOrder(harness.sessionRepository, harness.stageRepository);
        order.verify(harness.sessionRepository).lockAppendOrder(SESSION_ID);
        order.verify(harness.stageRepository, times(2)).findCurrentForUpdate(SESSION_ID);
        order.verify(harness.stageRepository).update(any());
        order.verify(harness.stageRepository).add(any());
    }

    @Test
    void everyStageMutationRejectsANonOngoingSessionBeforeTouchingStages() {
        when(harness.sessionRepository.lockAppendOrder(SESSION_ID)).thenReturn("generating");

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.ensureInitialStage(SessionFixtures.create(SESSION_ID)),
            "面试会话已结束或正在生成报告");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.moveToStage(SESSION_ID, "technical"),
            "面试会话已结束或正在生成报告");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.advanceIfReady(SESSION_ID),
            "面试会话已结束或正在生成报告");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.closeCurrentStage(SESSION_ID),
            "面试会话已结束或正在生成报告");

        verifyNoInteractions(harness.stageRepository, harness.messageRepository);
    }

    @Test
    void moveToStageRejectsBackwardTransitionWhenNoPendingAssistantPrompt() {
        harness.stages.add(harness.stage(SESSION_ID, "technical"));
        harness.messages.add(harness.userMessage(SESSION_ID, 1));

        ExceptionFixtures.assertBusinessException(() -> harness.stageManager.moveToStage(SESSION_ID, "warmup"))
            .hasMessageContaining("不可回退");
    }

    @Test
    void moveToStageBlocksWhileAssistantPromptIsUnanswered() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 1));

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.moveToStage(SESSION_ID, "technical"),
            "请先回答当前阶段的面试官提问");
    }

    @Test
    void stageOperationsFailWhenSessionHasNoStage() {
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.moveToStage(SESSION_ID, "technical"),
            "面试阶段不存在");
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.advanceIfReady(SESSION_ID),
            "面试阶段不存在");
    }

    @Test
    void quotaAdvancementUsesReplyCountsRatherThanPromptText() {
        harness.stages.add(harness.stage(SESSION_ID, "technical"));
        harness.messages.add(harness.systemMessage(SESSION_ID, 1, "custom stage boundary"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 2));

        assertThat(harness.stageManager.advanceIfReady(SESSION_ID).getStageName()).isEqualTo("technical");
        assertThat(harness.stages).hasSize(1);

        harness.messages.add(harness.userMessage(SESSION_ID, 3));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 4));

        assertThat(harness.stageManager.advanceIfReady(SESSION_ID).getStageName()).isEqualTo("deep_dive");
        assertThat(harness.stages).hasSize(2);
    }

    @Test
    void assistantRepliesCountOnlyMessagesAfterTheStageSequenceBoundary() {
        harness.stages.add(harness.stage(SESSION_ID, "technical"));
        harness.messages.add(harness.systemMessage(SESSION_ID, 1, "unrelated wording"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 2));
        harness.messages.add(harness.userMessage(SESSION_ID, 3));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 4));

        assertThat(harness.stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(2);
    }

    @Test
    void assistantRepliesUseStageTimeToExcludeEarlierReplies() {
        LocalDateTime startedAt = LocalDateTime.now();
        var technical = harness.stage(SESSION_ID, "technical");
        technical.setStartedAt(startedAt);
        harness.stages.add(technical);
        var earlier = harness.assistantMessage(SESSION_ID, 1);
        earlier.setCreatedAt(startedAt.minusSeconds(1));
        harness.messages.add(earlier);
        var prompt = harness.systemMessage(SESSION_ID, 2, "stage prompt");
        prompt.setCreatedAt(startedAt);
        harness.messages.add(prompt);
        var current = harness.assistantMessage(SESSION_ID, 3);
        current.setCreatedAt(startedAt.plusSeconds(1));
        harness.messages.add(current);

        assertThat(harness.stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(1);
    }

    @Test
    void sequenceBoundaryDisambiguatesStagesSharingTheSameDatabaseTimestamp() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 25, 12, 0, 0);
        var technical = harness.stage(SESSION_ID, "technical");
        technical.setStartedAt(startedAt);
        harness.stages.add(technical);
        var previousPrompt = harness.systemMessage(SESSION_ID, 1, "previous stage");
        previousPrompt.setCreatedAt(startedAt);
        harness.messages.add(previousPrompt);
        var previousReply = harness.assistantMessage(SESSION_ID, 2);
        previousReply.setCreatedAt(startedAt);
        harness.messages.add(previousReply);
        harness.messages.add(harness.userMessage(SESSION_ID, 3));
        var currentPrompt = harness.systemMessage(SESSION_ID, 4, "current stage");
        currentPrompt.setCreatedAt(startedAt);
        harness.messages.add(currentPrompt);
        var currentReply = harness.assistantMessage(SESSION_ID, 5);
        currentReply.setCreatedAt(startedAt);
        harness.messages.add(currentReply);

        assertThat(harness.stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(1);
    }

    @Test
    void closingClosesAfterItsReplyQuota() {
        var closing = harness.stage(SESSION_ID, "closing");
        harness.stages.add(closing);
        harness.messages.add(harness.systemMessage(SESSION_ID, 1, "closing"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 2));

        harness.stageManager.advanceIfReady(SESSION_ID);

        assertThat(harness.stages).hasSize(1);
        assertThat(closing.getEndedAt()).isNotNull();
    }

    @Test
    void unknownStageFailsInsteadOfFallingBackToWarmup() {
        harness.stages.add(harness.stage(SESSION_ID, "unknown"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 1));

        ExceptionFixtures.assertBusinessException(() -> harness.stageManager.advanceIfReady(SESSION_ID))
            .hasMessageContaining("无效的面试阶段");
        verify(harness.stageRepository, never()).add(any());
    }
}
