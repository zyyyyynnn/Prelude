package com.interview.interview.application;

import com.interview.interview.application.InterviewMessageService;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewMessageServiceTest {

    @Mock
    private InterviewMessageMapper interviewMessageMapper;

    private InterviewMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new InterviewMessageService(interviewMessageMapper);
    }

    @Test
    void insertMessageAssignsSeqZeroWhenNoLatestMessage() {
        Long sessionId = 7L;
        when(interviewMessageMapper.findLatest(sessionId)).thenReturn(null);
        when(interviewMessageMapper.add(any(InterviewMessage.class))).thenReturn(1);

        InterviewMessage result = messageService.insertMessage(sessionId, "user", "hello");

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(interviewMessageMapper).add(messageCaptor.capture());
        InterviewMessage inserted = messageCaptor.getValue();

        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getRole()).isEqualTo("user");
        assertThat(result.getContent()).isEqualTo("hello");
        assertThat(result.getSeqNum()).isEqualTo(0);
        assertThat(inserted.getSessionId()).isEqualTo(sessionId);
        assertThat(inserted.getRole()).isEqualTo("user");
        assertThat(inserted.getContent()).isEqualTo("hello");
        assertThat(inserted.getSeqNum()).isEqualTo(0);
    }

    @Test
    void insertMessageIncrementsLatestSeqNum() {
        Long sessionId = 7L;
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(4);
        when(interviewMessageMapper.findLatest(sessionId)).thenReturn(latest);
        when(interviewMessageMapper.add(any(InterviewMessage.class))).thenReturn(1);

        InterviewMessage result = messageService.insertMessage(sessionId, "assistant", "reply");

        assertThat(result.getSeqNum()).isEqualTo(5);
    }

    @Test
    void insertMessageHandlesNullLatestSeqNum() {
        Long sessionId = 7L;
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(null);
        when(interviewMessageMapper.findLatest(sessionId)).thenReturn(latest);
        when(interviewMessageMapper.add(any(InterviewMessage.class))).thenReturn(1);

        InterviewMessage result = messageService.insertMessage(sessionId, "system", "prompt");

        assertThat(result.getSeqNum()).isEqualTo(0);
    }

    @Test
    void invalidateSessionLockWithNullDoesNothing() {
        messageService.invalidateSessionLock(null);
        verify(interviewMessageMapper, never()).add(any(InterviewMessage.class));
    }

    @Test
    void invalidateSessionLockWithSessionIdDoesNotThrow() {
        messageService.invalidateSessionLock(7L);
        verify(interviewMessageMapper, never()).add(any(InterviewMessage.class));
    }
}
