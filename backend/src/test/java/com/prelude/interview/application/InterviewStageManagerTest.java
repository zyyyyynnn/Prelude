package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.interview.application.port.InterviewStageRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.domain.InterviewStagePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage-machine invariants: warmup bootstrap, forward-only transitions,
 * pending-assistant blocking, and stage-scoped reply counting.
 */
class InterviewStageManagerTest {

    private static final Long SESSION_ID = 42L;

    private final InterviewStageRepository stageRepository = mock(InterviewStageRepository.class);
    private final InterviewMessageRepository messageRepository = mock(InterviewMessageRepository.class);
    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private final InterviewStagePolicy stagePolicy = new InterviewStagePolicy();

    private InterviewStageManager stageManager;
    private final List<InterviewStage> stages = new ArrayList<>();
    private final List<InterviewMessage> messages = new ArrayList<>();
    private final AtomicLong messageSeq = new AtomicLong();

    @BeforeEach
    void setUp() {
        stageManager = new InterviewStageManager(
            stageRepository, messageRepository, messageService, stagePolicy);
        when(stageRepository.findCurrent(eq(SESSION_ID))).thenAnswer(invocation -> stages.stream()
            .filter(stage -> stage.getEndedAt() == null)
            .findFirst()
            .orElse(null));
        when(stageRepository.findLatest(eq(SESSION_ID))).thenAnswer(invocation ->
            stages.isEmpty() ? null : stages.get(stages.size() - 1));
        when(stageRepository.listBySession(eq(SESSION_ID))).thenAnswer(invocation -> List.copyOf(stages));
        when(stageRepository.add(any(InterviewStage.class))).thenAnswer(invocation -> {
            stages.add(invocation.getArgument(0));
            return 1;
        });
        when(stageRepository.update(any(InterviewStage.class))).thenReturn(1);
        when(messageRepository.listBySession(eq(SESSION_ID))).thenAnswer(invocation -> List.copyOf(messages));
        when(messageService.insertMessage(eq(SESSION_ID), anyString(), anyString()))
            .thenAnswer(invocation -> {
                InterviewMessage message = new InterviewMessage();
                message.setSessionId(SESSION_ID);
                message.setRole(invocation.getArgument(1));
                message.setContent(invocation.getArgument(2));
                message.setSeqNum((int) messageSeq.incrementAndGet());
                messages.add(message);
                return message;
            });
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(SESSION_ID);
        return session;
    }

    private InterviewStage stage(String name) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(SESSION_ID);
        stage.setStageName(name);
        stage.setStartedAt(java.time.LocalDateTime.now());
        return stage;
    }

    @Test
    void ensureInitialStageBootstrapsWarmupOnlyOnce() {
        stageManager.ensureInitialStage(session());
        stageManager.ensureInitialStage(session());

        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).getStageName()).isEqualTo("warmup");
        assertThat(stages.get(0).getEndedAt()).isNull();
    }

    @Test
    void currentStageNameDefaultsToWarmupWhenNoStageExists() {
        assertThat(stageManager.currentStageName(SESSION_ID)).isEqualTo("warmup");
    }

    @Test
    void moveToSameStageIsANoOp() {
        stages.add(stage("warmup"));

        InterviewStage result = stageManager.moveToStage(SESSION_ID, "warmup", false);

        assertThat(result.getStageName()).isEqualTo("warmup");
        assertThat(stages).hasSize(1);
        verify(messageService, never()).insertMessage(any(), anyString(), anyString());
    }

    @Test
    void moveToStageAdvancesAndInsertsADirectStagePrompt() {
        stages.add(stage("warmup"));

        InterviewStage advanced = stageManager.moveToStage(SESSION_ID, "technical", false);

        assertThat(advanced.getStageName()).isEqualTo("technical");
        assertThat(stages).hasSize(2);
        assertThat(stages.get(0).getEndedAt()).isNotNull();
        assertThat(stages.get(1).getEndedAt()).isNull();
        verify(messageService).insertMessage(
            eq(SESSION_ID), eq("system"),
            eq("面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。"));
    }

    @Test
    void moveToStageRejectsBackwardTransitionWhenNoPendingAssistantPrompt() {
        stages.add(stage("technical"));
        messages.add(userMessage(1));

        assertThatThrownBy(() -> stageManager.moveToStage(SESSION_ID, "warmup", false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不可回退");
    }

    @Test
    void moveToStageBlocksWhileAssistantPromptIsUnanswered() {
        stages.add(stage("warmup"));
        messages.add(assistantMessage(1));

        assertThatThrownBy(() -> stageManager.moveToStage(SESSION_ID, "technical", false))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请先回答当前阶段的面试官提问");
    }

    @Test
    void moveToStageFailsWhenSessionHasNoStage() {
        assertThatThrownBy(() -> stageManager.moveToStage(SESSION_ID, "technical", false))
            .isInstanceOf(BusinessException.class)
            .hasMessage("面试阶段不存在");
    }

    @Test
    void advanceStageWalksToTheNextStageAndClosesAfterClosing() {
        stages.add(stage("warmup"));

        stageManager.advanceStage(SESSION_ID, true);
        assertThat(stages).hasSize(2);
        assertThat(stages.get(1).getStageName()).isEqualTo("technical");
        verify(messageService).insertMessage(eq(SESSION_ID), eq("system"), anyString());

        stageManager.advanceStage(SESSION_ID, true);
        assertThat(stages.get(2).getStageName()).isEqualTo("deep_dive");

        stageManager.advanceStage(SESSION_ID, true);
        assertThat(stages.get(3).getStageName()).isEqualTo("closing");

        stageManager.advanceStage(SESSION_ID, false);
        assertThat(stages).hasSize(4);
        assertThat(stages.get(3).getEndedAt()).isNotNull();
    }

    @Test
    void advanceStageIsANoOpWithoutAnyStage() {
        stageManager.advanceStage(SESSION_ID, false);

        assertThat(stages).isEmpty();
        verify(stageRepository, never()).add(any());
    }

    @Test
    void assistantRepliesCountOnlyMessagesAfterTheLatestStagePrompt() {
        InterviewStage technical = stage("technical");
        stages.add(technical);
        messages.add(systemMessage(1, "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节进行追问。注意：如果技术问答已充分，准备进入深挖阶段，请在末尾严格附上 [STAGE_COMPLETE] 标识。"));
        messages.add(assistantMessage(2));
        messages.add(userMessage(3));
        messages.add(assistantMessage(4));

        assertThat(stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(2);
    }

    @Test
    void assistantRepliesCountFallsBackToStageStartTimeWithoutKnownPrompt() {
        InterviewStage custom = stage("warmup");
        custom.setStartedAt(java.time.LocalDateTime.now().minusMinutes(5));
        stages.add(custom);
        messages.add(assistantMessage(1));
        messages.add(systemMessage(2, "unrelated system note"));
        messages.add(assistantMessage(3));

        assertThat(stageManager.assistantRepliesInCurrentStage(SESSION_ID)).isEqualTo(2);
    }

    private InterviewMessage assistantMessage(int seqNum) {
        return message("assistant", "回答内容 " + seqNum, seqNum);
    }

    private InterviewMessage userMessage(int seqNum) {
        return message("user", "用户内容 " + seqNum, seqNum);
    }

    private InterviewMessage systemMessage(int seqNum, String content) {
        return message("system", content, seqNum);
    }

    private InterviewMessage message(String role, String content, int seqNum) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(SESSION_ID);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        return message;
    }
}
