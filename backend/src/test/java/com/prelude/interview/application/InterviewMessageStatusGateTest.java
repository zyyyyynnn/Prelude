package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prelude.BusinessException;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import org.junit.jupiter.api.Test;

/**
 * The status gate lives inside the append's row lock, so a turn that raced {@code FinishInterview}
 * is refused rather than writing into a generating or finished session.
 */
class InterviewMessageStatusGateTest {

    private final InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewMessageService service = new InterviewMessageService(messages, sessions);

    @Test
    void refusesAnAppendWhenTheLockedSessionIsAlreadyGenerating() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.GENERATING.wire());

        assertThatThrownBy(() -> service.insertMessage(9L, "user", "late"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已结束或正在生成报告");
        verify(messages, never()).add(any());
    }

    @Test
    void refusesAnAppendWhenTheLockedSessionIsFinished() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.FINISHED.wire());

        assertThatThrownBy(() -> service.insertMessage(9L, "assistant", "late"))
            .isInstanceOf(BusinessException.class);
        verify(messages, never()).add(any());
    }

    @Test
    void refusesAnAppendWhenTheSessionDisappearedUnderTheLock() {
        when(sessions.lockAppendOrder(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.insertMessage(9L, "user", "late"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不存在");
        verify(messages, never()).add(any());
    }

    @Test
    void appendsWhenTheLockedSessionIsStillOngoing() {
        when(sessions.lockAppendOrder(9L)).thenReturn(InterviewSessionStatus.ONGOING.wire());
        when(messages.findLatestForAppend(9L)).thenReturn(null);

        InterviewMessage inserted = service.insertMessage(9L, "user", "answer");

        assertThat(inserted.getSeqNum()).isZero();
        verify(messages).add(any());
    }
}
