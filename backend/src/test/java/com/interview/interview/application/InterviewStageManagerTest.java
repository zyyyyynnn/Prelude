package com.interview.interview.application;

import com.interview.interview.application.InterviewMessageService;
import com.interview.interview.application.InterviewStageManager;

import com.interview.shared.api.BusinessException;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewStageMapper;
import com.interview.interview.domain.InterviewStagePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewStageManagerTest {

    private static final String COMPLETION_TECHNICAL_PROMPT = "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节进行追问。注意：如果技术问答已充分，准备进入深挖阶段，请在末尾严格附上 [STAGE_COMPLETE] 标识。";
    private static final String DIRECT_TECHNICAL_PROMPT = "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。";

    @Mock
    private InterviewStageMapper interviewStageMapper;
    @Mock
    private InterviewMessageMapper interviewMessageMapper;
    @Mock
    private InterviewMessageService interviewMessageService;

    private InterviewStageManager stageManager;

    @BeforeEach
    void setUp() {
        stageManager = new InterviewStageManager(
            interviewStageMapper,
            interviewMessageMapper,
            interviewMessageService,
            new InterviewStagePolicy()
        );
    }

    @Test
    void moveToStageAdvancesAndInsertsSystemMessageViaMessageService() {
        Long sessionId = 7L;
        InterviewStage warmup = stage(sessionId, "warmup", LocalDateTime.now().minusMinutes(5), null);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(warmup);
        when(interviewStageMapper.update(any(InterviewStage.class))).thenReturn(1);
        when(interviewStageMapper.add(any(InterviewStage.class))).thenAnswer(invocation -> {
            InterviewStage stage = invocation.getArgument(0);
            stage.setId(100L);
            return 1;
        });
        when(interviewMessageService.insertMessage(sessionId, "system", COMPLETION_TECHNICAL_PROMPT))
            .thenReturn(new InterviewMessage());

        InterviewStage nextStage = stageManager.moveToStage(sessionId, "technical", true);

        assertThat(nextStage.getStageName()).isEqualTo("technical");
        verify(interviewStageMapper, times(1)).update(warmup);
        verify(interviewStageMapper, times(1)).add(any(InterviewStage.class));
        verify(interviewMessageService).insertMessage(sessionId, "system", COMPLETION_TECHNICAL_PROMPT);
        verify(interviewMessageMapper, never()).add(any(InterviewMessage.class));
    }

    @Test
    void advanceStageInsertsSystemMessageForNextStage() {
        Long sessionId = 7L;
        InterviewStage warmup = stage(sessionId, "warmup", LocalDateTime.now().minusMinutes(5), null);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(warmup);
        when(interviewStageMapper.update(any(InterviewStage.class))).thenReturn(1);
        when(interviewStageMapper.add(any(InterviewStage.class))).thenAnswer(invocation -> {
            InterviewStage stage = invocation.getArgument(0);
            stage.setId(101L);
            return 1;
        });
        when(interviewMessageService.insertMessage(sessionId, "system", DIRECT_TECHNICAL_PROMPT))
            .thenReturn(new InterviewMessage());

        stageManager.advanceStage(sessionId, false);

        verify(interviewMessageService).insertMessage(sessionId, "system", DIRECT_TECHNICAL_PROMPT);
    }

    @Test
    void advanceStageAtClosingOnlyClosesCurrentStage() {
        Long sessionId = 7L;
        InterviewStage closing = stage(sessionId, "closing", LocalDateTime.now().minusMinutes(5), null);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(closing);
        when(interviewStageMapper.update(any(InterviewStage.class))).thenReturn(1);

        stageManager.advanceStage(sessionId, true);

        verify(interviewStageMapper, times(1)).update(closing);
        verify(interviewStageMapper, never()).add(any(InterviewStage.class));
        verify(interviewMessageService, never()).insertMessage(any(), any(), any());
    }

    @Test
    void moveToStageRejectsBackwardStageTransition() {
        Long sessionId = 7L;
        InterviewStage technical = stage(sessionId, "technical", LocalDateTime.now().minusMinutes(5), null);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(technical);

        assertThatThrownBy(() -> stageManager.moveToStage(sessionId, "warmup", true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("面试阶段不可回退");
    }

    @Test
    void moveToStageRejectsSkipStageTransition() {
        Long sessionId = 7L;
        InterviewStage warmup = stage(sessionId, "warmup", LocalDateTime.now().minusMinutes(5), null);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(warmup);

        assertThatThrownBy(() -> stageManager.moveToStage(sessionId, "deep_dive", true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("阶段推进顺序不正确");
    }

    @Test
    void moveToStageRejectsPendingAssistantPrompt() {
        Long sessionId = 7L;
        InterviewStage warmup = stage(sessionId, "warmup", LocalDateTime.now().minusMinutes(5), null);
        InterviewMessage assistantMsg = new InterviewMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("问题？");
        assistantMsg.setSeqNum(1);

        when(interviewStageMapper.findCurrent(sessionId)).thenReturn(warmup);
        when(interviewMessageMapper.listBySession(sessionId)).thenReturn(List.of(assistantMsg));

        assertThatThrownBy(() -> stageManager.moveToStage(sessionId, "technical", true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("请先回答当前阶段的面试官提问");
    }

    private InterviewStage stage(Long sessionId, String stageName, LocalDateTime startedAt, LocalDateTime endedAt) {
        InterviewStage stage = new InterviewStage();
        stage.setId(1L);
        stage.setSessionId(sessionId);
        stage.setStageName(stageName);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        return stage;
    }
}
