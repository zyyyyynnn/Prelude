package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prelude.BusinessException;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.integration.InterviewSessionStatus;
import org.junit.jupiter.api.Test;

class InterviewMessageStatusGateTest {

    private final InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewStageRepository stages = mock(InterviewStageRepository.class);
    private final InterviewStage stage = new InterviewStage();
    private final InterviewMessageService service = new InterviewMessageService(messages, sessions, stages);

    InterviewMessageStatusGateTest() {
        stage.setStageName("warmup");
        when(stages.findCurrentForUpdate(9L)).thenReturn(stage);
    }

    @Test
    void refusesAnAppendWhenTheLockedSessionIsAlreadyGenerating() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.GENERATING);

        assertThatThrownBy(() -> service.insertUserMessage(9L, "late"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已结束或正在生成报告");
        verify(messages, never()).add(any());
        verify(stages, never()).update(any());
    }

    @Test
    void refusesAnAppendWhenTheLockedSessionIsFinished() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.FINISHED);

        assertThatThrownBy(() -> service.insertAssistantMessage(9L, "late"))
            .isInstanceOf(BusinessException.class);
        verify(messages, never()).add(any());
    }

    @Test
    void refusesAnAppendWhenTheSessionDisappearedUnderTheLock() {
        when(sessions.lockAppendOrder(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.insertUserMessage(9L, "answer"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不存在");
        verify(messages, never()).add(any());
    }

    @Test
    void appendsWhenTheLockedSessionIsStillOngoing() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        when(messages.findLatestForAppend(9L)).thenReturn(null);

        InterviewMessage inserted = service.insertUserMessage(9L, "answer");

        assertThat(inserted.getSeqNum()).isZero();
        assertThat(inserted.getAssessmentStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(stage.isAwaitingAnswer()).isFalse();
        verify(stages).update(stage);
        verify(messages).add(any());
    }

    @Test
    void assistantMessagesAwaitAnAnswerAndSystemMessagesDoNot() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        when(messages.findLatestForAppend(9L)).thenReturn(null);

        assertThat(service.insertAssistantMessage(9L, "question").getAssessmentStatus()).isNull();
        assertThat(stage.isAwaitingAnswer()).isTrue();
        assertThat(service.insertSystemMessage(9L, "context").getAssessmentStatus()).isNull();
        assertThat(stage.isAwaitingAnswer()).isTrue();

        service.insertUserMessage(9L, "answer");
        assertThat(stage.isAwaitingAnswer()).isFalse();
    }

    @Test
    void reAnswerMarksThePreviousAnswerAsSuperseded() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        when(messages.findLatestForAppend(9L)).thenReturn(null);
        InterviewMessage previous = new InterviewMessage();
        previous.setId(4L);
        previous.setSessionId(9L);
        previous.setRole("user");
        when(messages.findById(4L)).thenReturn(previous);
        when(messages.markSuperseded(4L, 5L)).thenReturn(1);
        doAnswer(invocation -> {
            InterviewMessage inserted = invocation.getArgument(0);
            inserted.setId(5L);
            return 1;
        }).when(messages).add(any());

        InterviewMessage inserted = service.insertUserMessage(9L, "corrected answer", 4L);

        assertThat(inserted.getId()).isEqualTo(5L);
        verify(messages).markSuperseded(4L, 5L);
    }

    @Test
    void anExistingAssistantReplyIsNotInsertedTwice() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        InterviewMessage user = new InterviewMessage();
        user.setId(4L);
        user.setSessionId(9L);
        user.setRole("user");
        when(messages.findById(4L)).thenReturn(user);
        when(messages.findByReplyToMessageId(4L)).thenReturn(new InterviewMessage());

        assertThat(service.insertAssistantMessage(9L, "duplicate", 4L)).isNull();
        verify(messages, never()).add(any());
    }

    @Test
    void aSupersededAnswerCannotReceiveAnotherAssistantReply() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        InterviewMessage user = new InterviewMessage();
        user.setId(4L);
        user.setSessionId(9L);
        user.setRole("user");
        user.setSupersededByMessageId(5L);
        when(messages.findById(4L)).thenReturn(user);

        assertThatThrownBy(() -> service.insertAssistantMessage(9L, "late reply", 4L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已被取代");
        verify(messages, never()).add(any());
    }

    @Test
    void conversationMessagesRequireAnExplicitStage() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING);
        when(stages.findCurrentForUpdate(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.insertAssistantMessage(9L, "question"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("面试阶段不存在");
        verify(messages, never()).add(any());
    }
}
