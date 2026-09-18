package com.prelude.interview.application;

import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import com.prelude.test.SessionFixtures.StageManagerHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Stage-machine invariants: warmup bootstrap, forward-only transitions,
 * pending-assistant blocking, and stage-scoped reply counting.
 */
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
    void currentStageNameDefaultsToWarmupWhenNoStageExists() {
        assertThat(harness.stageManager.currentStageName(SESSION_ID)).isEqualTo("warmup");
    }

    @Test
    void moveToSameStageIsANoOp() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        var result = harness.stageManager.moveToStage(SESSION_ID, "warmup", false);

        assertThat(result.getStageName()).isEqualTo("warmup");
        assertThat(harness.stages).hasSize(1);
        verify(messageService, never()).insertMessage(any(), anyString(), anyString());
    }

    @Test
    void moveToStageAdvancesAndInsertsADirectStagePrompt() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        var advanced = harness.stageManager.moveToStage(SESSION_ID, "technical", false);

        assertThat(advanced.getStageName()).isEqualTo("technical");
        assertThat(harness.stages).hasSize(2);
        assertThat(harness.stages.get(0).getEndedAt()).isNotNull();
        assertThat(harness.stages.get(1).getEndedAt()).isNull();
        verify(messageService).insertMessage(
            eq(SESSION_ID), eq("system"),
            eq("面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。"));
    }

    @Test
    void moveToStageRejectsBackwardTransitionWhenNoPendingAssistantPrompt() {
        harness.stages.add(harness.stage(SESSION_ID, "technical"));
        harness.messages.add(harness.userMessage(SESSION_ID, 1));

        ExceptionFixtures.assertBusinessException(() -> harness.stageManager.moveToStage(SESSION_ID, "warmup", false))
            .hasMessageContaining("不可回退");
    }

    @Test
    void moveToStageBlocksWhileAssistantPromptIsUnanswered() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 1));

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.moveToStage(SESSION_ID, "technical", false),
            "请先回答当前阶段的面试官提问");
    }

    @Test
    void moveToStageFailsWhenSessionHasNoStage() {
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> harness.stageManager.moveToStage(SESSION_ID, "technical", false),
            "面试阶段不存在");
    }

    @Test
    void advanceStageWalksToTheNextStageAndClosesAfterClosing() {
        harness.stages.add(harness.stage(SESSION_ID, "warmup"));

        harness.stageManager.advanceStage(SESSION_ID, true);
        assertThat(harness.stages).hasSize(2);
        assertThat(harness.stages.get(1).getStageName()).isEqualTo("technical");
        verify(messageService).insertMessage(eq(SESSION_ID), eq("system"), anyString());

        harness.stageManager.advanceStage(SESSION_ID, true);
        assertThat(harness.stages.get(2).getStageName()).isEqualTo("deep_dive");

        harness.stageManager.advanceStage(SESSION_ID, true);
        assertThat(harness.stages.get(3).getStageName()).isEqualTo("closing");

        harness.stageManager.advanceStage(SESSION_ID, false);
        assertThat(harness.stages).hasSize(4);
        assertThat(harness.stages.get(3).getEndedAt()).isNotNull();
    }

    @Test
    void advanceStageIsANoOpWithoutAnyStage() {
        harness.stageManager.advanceStage(SESSION_ID, false);

        assertThat(harness.stages).isEmpty();
        verify(harness.stageRepository, never()).add(any());
    }

    @Test
    void assistantRepliesCountOnlyMessagesAfterTheLatestStagePrompt() {
        var technical = harness.stage(SESSION_ID, "technical");
        harness.stages.add(technical);
        harness.messages.add(harness.systemMessage(SESSION_ID, 1, "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节进行追问。注意：如果技术问答已充分，准备进入深挖阶段，请在末尾严格附上 [STAGE_COMPLETE] 标识。"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 2));
        harness.messages.add(harness.userMessage(SESSION_ID, 3));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 4));

        assertThat(harness.stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(2);
    }

    @Test
    void assistantRepliesCountFallsBackToStageStartTimeWithoutKnownPrompt() {
        var custom = harness.stage(SESSION_ID, "warmup");
        custom.setStartedAt(java.time.LocalDateTime.now().minusMinutes(5));
        harness.stages.add(custom);
        harness.messages.add(harness.assistantMessage(SESSION_ID, 1));
        harness.messages.add(harness.systemMessage(SESSION_ID, 2, "unrelated system note"));
        harness.messages.add(harness.assistantMessage(SESSION_ID, 3));

        assertThat(harness.stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(2);
    }
}