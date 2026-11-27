package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.interview.application.port.InterviewContextPort;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

class RunInterviewTurnRetryTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final ModelExecutionPort llm = mock(ModelExecutionPort.class);
    private final InterviewContextPort context = mock(InterviewContextPort.class);
    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private final AttachmentContextPort attachments = mock(AttachmentContextPort.class);
    private final InterviewTurnCompletion completion = mock(InterviewTurnCompletion.class);
    private final InterviewJudgeService judge = mock(InterviewJudgeService.class);
    private final InterviewSummaryService summary = mock(InterviewSummaryService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> redisValues = mock(ValueOperations.class);
    private final InterviewTurnSink sink = mock(InterviewTurnSink.class);
    private final RunInterviewTurn service = new RunInterviewTurn(
        sessionAccess, messages, sessions, llm, context, messageService,
        attachments, completion, judge, summary, redis);

    @Test
    void retryAndSupersedeAreRejectedBeforeAnyStateIsRead() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.execute(new InterviewTurnCommand(51L, 7L, "ignored", false, 9L, 8L), sink))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessageContaining("不能同时指定");

        verifyNoInteractions(sessionAccess, messages, sessions, redis, llm, context, completion, messageService);
    }

    @Test
    void retryReturnsTheExistingReplyWithoutCallingTheModelAgain() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setTargetPosition("Engineer");
        session.setModelExecutionSnapshotId(3L);
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session);
        when(messages.findById(9L)).thenReturn(message(9L, "user", "answer", null));
        when(messages.findByReplyToMessageId(9L)).thenReturn(message(10L, "assistant", "existing reply", 9L));

        var result = service.execute(new InterviewTurnCommand(51L, 7L, "ignored", false, 9L, null), sink);

        assertThat(result.assistantReply()).isEqualTo("existing reply");
        assertThat(result.userTurn().messageId()).isEqualTo(9L);
        verify(sink).assistantDelta("existing reply");
        verifyNoInteractions(llm, context, completion, messageService);
    }

    @Test
    void aNewAnswerLocksItsReplyBeforeStreamingStarts() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setTargetPosition("Engineer");
        session.setModelExecutionSnapshotId(3L);
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session);
        when(messages.countConversationMessages(51L)).thenReturn(1L);
        when(messageService.insertUserMessage(51L, "answer", null)).thenReturn(message(9L, "user", "answer", null));
        when(context.buildContextMessages(51L)).thenReturn(List.of());
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((com.prelude.llm.api.ModelExecutionPort.StreamSink) invocation.getArgument(1)).onNext("reply");
            return null;
        }).when(llm).stream(any(), any());

        var result = service.execute(new InterviewTurnCommand(51L, 7L, "answer", false), sink);

        assertThat(result.assistantReply()).isEqualTo("reply");
        verify(redisValues).setIfAbsent(
            org.mockito.ArgumentMatchers.eq("lock:interview-reply:9"),
            any(),
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        verify(completion).complete(51L, 9L, "reply");
    }

    @Test
    void concurrentRetryWithoutACompletedReplyIsRejected() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session);
        when(messages.findById(9L)).thenReturn(message(9L, "user", "answer", null));
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.execute(new InterviewTurnCommand(51L, 7L, "ignored", false, 9L, null), sink))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessageContaining("正在处理中");
        verifyNoInteractions(llm, completion);
    }

    @Test
    void retryRejectsAMessageFromAnotherSession() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session);
        InterviewMessage foreign = message(9L, "user", "answer", null);
        foreign.setSessionId(52L);
        when(messages.findById(9L)).thenReturn(foreign);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.execute(new InterviewTurnCommand(51L, 7L, "ignored", false, 9L, null), sink))
            .isInstanceOf(com.prelude.BusinessException.class);

        verify(messages, never()).findByReplyToMessageId(any());
        verifyNoInteractions(llm, completion);
    }

    private InterviewMessage message(long id, String role, String content, Long replyToMessageId) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setSessionId(51L);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum((int) id);
        message.setReplyToMessageId(replyToMessageId);
        return message;
    }
}
