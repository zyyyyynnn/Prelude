package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.api.AssetRef;
import com.prelude.interview.application.port.InterviewContextPort;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;

/**
 * The first auto-start turn carries only image attachments into the model call;
 * non-image context stays in the prompt and never becomes multimodal input.
 */
class RunInterviewTurnVisionAttachmentTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewMessageRepository messageRepository = mock(InterviewMessageRepository.class);
    private final ModelExecutionPort llmPort = mock(ModelExecutionPort.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final InterviewContextPort contextPort = mock(InterviewContextPort.class);
    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final InterviewTurnCompletion completion = mock(InterviewTurnCompletion.class);
    private final InterviewJudgeService judgeService = mock(InterviewJudgeService.class);
    private final InterviewSummaryService summaryService = mock(InterviewSummaryService.class);
    private final InterviewTurnSink sink = mock(InterviewTurnSink.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> redisValues = mock(ValueOperations.class);

    private final RunInterviewTurn runInterviewTurn = new RunInterviewTurn(
        sessionAccess,
        messageRepository,
        sessionRepository,
        llmPort,
        contextPort,
        messageService,
        attachmentContextPort,
        completion,
        judgeService,
        summaryService,
        redis
    );

    @BeforeEach
    void setUpRedis() {
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setTargetPosition("前端工程师");
        session.setModelExecutionSnapshotId(3L);
        return session;
    }

    @Test
    void repeatedAutoStartReturnsTheExistingOpeningWithoutCallingTheModel() {
        InterviewMessage opening = new InterviewMessage();
        opening.setId(10L);
        opening.setSessionId(51L);
        opening.setRole("assistant");
        opening.setContent("existing opening");
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(1L);
        when(messageRepository.findLatest(51L)).thenReturn(opening);

        var result = runInterviewTurn.execute(new InterviewTurnCommand(51L, 7L, "", true), sink);

        assertThat(result.assistantReply()).isEqualTo("existing opening");
        verify(sink).assistantDelta("existing opening");
        verifyNoInteractions(llmPort, contextPort, completion);
    }

    @Test
    void concurrentAutoStartWithoutAnOpeningIsRejected() {
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(0L);
        when(redisValues.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> runInterviewTurn.execute(new InterviewTurnCommand(51L, 7L, "", true), sink))
            .hasMessageContaining("面试正在开始");
        verifyNoInteractions(llmPort, contextPort, completion);
    }

    @Test
    void theFirstAutoStartTurnSendsImageAttachmentsToTheModel() {
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(0L);
        when(contextPort.buildAutoStartMessages(any()))
            .thenReturn(List.of(Map.of("role", "user", "content", "开始")));
        AssetRef imageRef = new AssetRef(11L);
        AssetRef pdfRef = new AssetRef(12L);
        when(attachmentContextPort.list(7L, "interview", 51L)).thenReturn(List.of(
            new AttachmentSnapshot(8L, "diagram.png", "image/png", 12L, true, null, imageRef),
            new AttachmentSnapshot(9L, "notes.pdf", "application/pdf", 20L, false, "text", pdfRef)
        ));
        when(attachmentContextPort.readOwnedContent(7L, imageRef)).thenReturn(new byte[] {1, 2, 3});
        doAnswer(invocation -> {
            ModelExecutionPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("你好");
            return null;
        }).when(llmPort).stream(any(), any());

        runInterviewTurn.execute(
            new InterviewTurnCommand(51L, 7L, "", true), sink);

        ArgumentCaptor<ModelExecutionPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(ModelExecutionPort.ModelExecutionRequest.class);
        verify(llmPort).stream(request.capture(), any());
        List<ModelExecutionPort.Attachment> attachments = request.getValue().attachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).fileName()).isEqualTo("diagram.png");
        assertThat(attachments.get(0).mediaType()).isEqualTo("image/png");
        assertThat(attachments.get(0).content()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        verify(attachmentContextPort).readOwnedContent(7L, imageRef);
        verify(attachmentContextPort, never()).readOwnedContent(eq(7L), eq(pdfRef));
    }

    @Test
    void modelStageMarkerIsOrdinaryReplyTextAndDoesNotAdvanceTheStage() {
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(2L);
        when(messageService.insertUserMessage(51L, "回答", null)).thenReturn(acceptedMessage());
        when(messageRepository.findById(9L)).thenReturn(acceptedMessage());
        when(contextPort.buildContextMessages(51L)).thenReturn(List.of());
        doAnswer(invocation -> {
            ModelExecutionPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("回答完毕[STAGE_COMPLETE]");
            return null;
        }).when(llmPort).stream(any(), any());

        var result = runInterviewTurn.execute(new InterviewTurnCommand(51L, 7L, "回答", false), sink);

        assertThat(result.assistantReply()).isEqualTo("回答完毕[STAGE_COMPLETE]");
        verify(completion).complete(51L, 9L, "回答完毕[STAGE_COMPLETE]");
    }

    @Test
    void llmFailureKeepsTheAcceptedUserMessage() {
        InterviewMessage accepted = acceptedMessage();
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(2L);
        when(messageService.insertUserMessage(51L, "回答", null)).thenReturn(accepted);
        when(messageRepository.findById(9L)).thenReturn(accepted);
        org.mockito.Mockito.doThrow(new IllegalStateException("model unavailable")).when(llmPort).stream(any(), any());

        assertThatThrownBy(() -> runInterviewTurn.execute(new InterviewTurnCommand(51L, 7L, "回答", false), sink))
            .hasMessage("model unavailable");

        verify(sink).userAccepted(org.mockito.ArgumentMatchers.argThat(turn -> turn.messageId().equals(9L)));
        verifyNoInteractions(completion);
        verify(messageRepository).countConversationMessages(51L);
        verify(messageRepository).findById(9L);
        verifyNoMoreInteractions(messageRepository);
    }

    @Test
    void completionFailureDoesNotDeleteTheAcceptedUserMessage() {
        InterviewMessage accepted = acceptedMessage();
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(2L);
        when(messageService.insertUserMessage(51L, "回答", null)).thenReturn(accepted);
        when(messageRepository.findById(9L)).thenReturn(accepted);
        when(contextPort.buildContextMessages(51L)).thenReturn(List.of());
        doAnswer(invocation -> {
            ModelExecutionPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("助手回复");
            return null;
        }).when(llmPort).stream(any(), any());
        org.mockito.Mockito.doThrow(new IllegalStateException("stage persistence failed"))
            .when(completion).complete(51L, 9L, "助手回复");

        assertThatThrownBy(() -> runInterviewTurn.execute(new InterviewTurnCommand(51L, 7L, "回答", false), sink))
            .hasMessage("stage persistence failed");

        verify(sink).userAccepted(org.mockito.ArgumentMatchers.argThat(turn -> turn.messageId().equals(9L)));
        verify(messageRepository).countConversationMessages(51L);
        verify(messageRepository).findById(9L);
        verifyNoMoreInteractions(messageRepository);
    }

    private InterviewMessage acceptedMessage() {
        InterviewMessage message = new InterviewMessage();
        message.setId(9L);
        message.setSessionId(51L);
        message.setRole("user");
        message.setContent("回答");
        message.setSeqNum(2);
        return message;
    }

    @Test
    void aNormalTurnDoesNotAttachFiles() {
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(2L);
        when(messageService.insertUserMessage(51L, "回答", null))
            .thenReturn(acceptedMessage());
        when(messageRepository.findById(9L)).thenReturn(acceptedMessage());
        when(contextPort.buildContextMessages(51L))
            .thenReturn(List.of(Map.of("role", "user", "content", "回答")));
        doAnswer(invocation -> {
            ModelExecutionPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("好的");
            return null;
        }).when(llmPort).stream(any(), any());

        runInterviewTurn.execute(
            new InterviewTurnCommand(51L, 7L, "回答", false), sink);

        ArgumentCaptor<ModelExecutionPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(ModelExecutionPort.ModelExecutionRequest.class);
        verify(llmPort).stream(request.capture(), any());
        assertThat(request.getValue().attachments()).isEmpty();
        verify(attachmentContextPort, never()).list(anyLong(), anyString(), anyLong());
    }
}
