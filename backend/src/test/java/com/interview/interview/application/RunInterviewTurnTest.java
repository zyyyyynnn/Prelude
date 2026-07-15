package com.interview.interview.application;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.platform.llm.ChatPort;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunInterviewTurnTest {

    @Mock private InterviewSessionMapper sessionMapper;
    @Mock private InterviewMessageMapper messageMapper;
    @Mock private ChatPort chatPort;
    @Mock private InterviewFixturePort devFixtureService;
    @Mock private InterviewStageManager stageManager;
    @Mock private InterviewContextService contextService;

    private RunInterviewTurn useCase;

    @BeforeEach
    void setUp() {
        useCase = new RunInterviewTurn(
            new InterviewSessionAccess(sessionMapper),
            messageMapper,
            chatPort,
            devFixtureService,
            stageManager,
            contextService,
            new InterviewMessageService(messageMapper)
        );
    }

    @Test
    void persistsOneSharedTurnAndAdvancesWithChannelPromptPolicy() {
        InterviewSession session = ongoingSession();
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(messageMapper.countConversationMessages(7L)).thenReturn(2L);
        when(contextService.buildContextMessages(7L)).thenReturn(List.of(Map.of("role", "user", "content", "answer")));
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(messageMapper.add(any(InterviewMessage.class))).thenAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            message.setId("user".equals(message.getRole()) ? 101L : 102L);
            return 1;
        });
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> deltas = invocation.getArgument(1);
            deltas.accept("next question [STAGE_COMPLETE]");
            return null;
        }).when(chatPort).stream(any(), any());
        InterviewTurnSink sink = org.mockito.Mockito.mock(InterviewTurnSink.class);

        InterviewTurnResult result = useCase.execute(
            new InterviewTurnCommand(7L, 42L, " answer ", false, false), sink
        );

        assertThat(result.userMessage().getContent()).isEqualTo("answer");
        assertThat(result.assistantReply()).isEqualTo("next question");
        verify(sink).userAccepted(result.userMessage());
        verify(sink).assistantDelta("next question [STAGE_COMPLETE]");
        verify(stageManager).advanceStage(7L, false);

        ArgumentCaptor<InterviewMessage> messages = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).add(messages.capture());
        assertThat(messages.getAllValues()).extracting(InterviewMessage::getRole)
            .containsExactly("user", "assistant");
    }

    @Test
    void rollsBackInsertedUserMessageWhenStreamingFails() {
        InterviewSession session = ongoingSession();
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(messageMapper.countConversationMessages(7L)).thenReturn(1L);
        when(contextService.buildContextMessages(7L)).thenReturn(List.of());
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(messageMapper.add(any(InterviewMessage.class))).thenAnswer(invocation -> {
            InterviewMessage message = invocation.getArgument(0);
            message.setId(101L);
            return 1;
        });
        doThrow(new RuntimeException("upstream down")).when(chatPort).stream(any(), any());

        assertThatThrownBy(() -> useCase.execute(
            new InterviewTurnCommand(7L, 42L, "answer", false, true), delta -> {
            }
        )).hasMessage("upstream down");

        verify(messageMapper).delete(101L);
        verify(stageManager, never()).advanceStage(eq(7L), any(Boolean.class));
    }

    private InterviewSession ongoingSession() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setStatus("ongoing");
        session.setLlmProvider("openai-responses");
        session.setLlmModel("model-a");
        return session;
    }
}
