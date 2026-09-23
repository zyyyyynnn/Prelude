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
import static org.mockito.Mockito.when;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.api.AssetRef;
import com.prelude.interview.application.port.InterviewContextPort;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.LlmPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The first auto-start turn carries only image attachments into the model call;
 * non-image context stays in the prompt and never becomes multimodal input.
 */
class RunInterviewTurnVisionAttachmentTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewMessageRepository messageRepository = mock(InterviewMessageRepository.class);
    private final LlmPort llmPort = mock(LlmPort.class);
    private final InterviewStageManager stageManager = mock(InterviewStageManager.class);
    private final InterviewContextPort contextPort = mock(InterviewContextPort.class);
    private final InterviewMessageService messageService = mock(InterviewMessageService.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final InterviewTurnSink sink = mock(InterviewTurnSink.class);

    private final RunInterviewTurn runInterviewTurn = new RunInterviewTurn(
        sessionAccess,
        messageRepository,
        llmPort,
        stageManager,
        contextPort,
        messageService,
        attachmentContextPort
    );

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(7L);
        session.setTargetPosition("前端工程师");
        session.setModelExecutionSnapshotId(3L);
        return session;
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
            LlmPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("你好");
            return null;
        }).when(llmPort).stream(any(), any());

        runInterviewTurn.execute(
            new InterviewTurnCommand(51L, 7L, "", true, false), sink);

        ArgumentCaptor<LlmPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(LlmPort.ModelExecutionRequest.class);
        verify(llmPort).stream(request.capture(), any());
        List<LlmPort.Attachment> attachments = request.getValue().attachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).fileName()).isEqualTo("diagram.png");
        assertThat(attachments.get(0).mediaType()).isEqualTo("image/png");
        assertThat(attachments.get(0).content()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        verify(attachmentContextPort).readOwnedContent(7L, imageRef);
        verify(attachmentContextPort, never()).readOwnedContent(eq(7L), eq(pdfRef));
    }

    @Test
    void aNormalTurnDoesNotAttachFiles() {
        when(sessionAccess.requireOngoing(51L, 7L)).thenReturn(session());
        when(messageRepository.countConversationMessages(51L)).thenReturn(2L);
        when(messageService.insertMessage(51L, "user", "回答"))
            .thenReturn(new com.prelude.interview.domain.InterviewMessage());
        when(contextPort.buildContextMessages(51L))
            .thenReturn(List.of(Map.of("role", "user", "content", "回答")));
        doAnswer(invocation -> {
            LlmPort.StreamSink streamSink = invocation.getArgument(1);
            streamSink.onNext("好的");
            return null;
        }).when(llmPort).stream(any(), any());

        runInterviewTurn.execute(
            new InterviewTurnCommand(51L, 7L, "回答", false, false), sink);

        ArgumentCaptor<LlmPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(LlmPort.ModelExecutionRequest.class);
        verify(llmPort).stream(request.capture(), any());
        assertThat(request.getValue().attachments()).isEmpty();
        verify(attachmentContextPort, never()).list(anyLong(), anyString(), anyLong());
    }
}
