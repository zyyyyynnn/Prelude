package com.interview.interview.application;

import com.interview.interview.application.port.InterviewFixturePort;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.interview.application.port.InterviewSessionRepository;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.PromptRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSummaryServiceTest {

    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewMessageRepository messageRepository;
    @Mock private ChatPort chatPort;
    @Mock private InterviewFixturePort fixturePort;
    @Mock private PromptRegistry promptRegistry;

    private InterviewSummaryService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSummaryService(
            sessionRepository,
            messageRepository,
            chatPort,
            fixturePort,
            promptRegistry,
            Runnable::run
        );
    }

    @Test
    void doesNotSummarizeBeforeSlidingWindowThreshold() {
        InterviewSession session = session();
        when(messageRepository.listBySession(7L)).thenReturn(dialogMessages(28));

        service.triggerAsyncSummarizeIfNeeded(session, false);

        verify(sessionRepository, never()).update(any());
        verify(chatPort, never()).complete(any());
    }

    @Test
    void fixtureSummaryRunsAtThresholdAndExcludesSystemMessagesFromRoundCount() {
        InterviewSession session = session();
        List<InterviewMessage> messages = new ArrayList<>();
        messages.add(message("system", "prompt"));
        messages.addAll(dialogMessages(30));
        when(messageRepository.listBySession(7L)).thenReturn(messages);
        when(fixturePort.isEnabled()).thenReturn(true);

        service.triggerAsyncSummarizeIfNeeded(session, true);

        assertThat(session.getSummary()).contains("模拟对话摘要", "后端架构设计");
        verify(sessionRepository).update(session);
        verify(chatPort, never()).complete(any());
    }

    @Test
    void productionSummaryContainsExistingSummaryAndOnlySummarizedPrefix() {
        InterviewSession session = session();
        session.setSummary("已有摘要");
        when(messageRepository.listBySession(7L)).thenReturn(dialogMessages(40));
        when(fixturePort.isEnabled()).thenReturn(false);
        when(promptRegistry.load("interview.summary", "v1")).thenReturn("summary system prompt");
        when(chatPort.complete(any(ChatRequest.class))).thenReturn("新摘要");

        service.triggerAsyncSummarizeIfNeeded(session, false);

        assertThat(session.getSummary()).isEqualTo("新摘要");
        verify(sessionRepository).update(session);
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatPort).complete(requestCaptor.capture());
        ChatRequest request = requestCaptor.getValue();
        assertThat(request.userId()).isEqualTo(42L);
        assertThat(request.messages().getFirst()).containsEntry("content", "summary system prompt");
        assertThat(request.messages().get(1).get("content"))
            .contains("已有摘要", "dialog-0", "dialog-25")
            .doesNotContain("dialog-26");
    }

    @Test
    void summaryFailureLeavesSessionUnchanged() {
        InterviewSession session = session();
        when(messageRepository.listBySession(7L)).thenReturn(dialogMessages(30));
        when(fixturePort.isEnabled()).thenReturn(false);
        when(promptRegistry.load("interview.summary", "v1")).thenReturn("summary system prompt");
        when(chatPort.complete(any(ChatRequest.class))).thenThrow(new RuntimeException("llm down"));

        service.triggerAsyncSummarizeIfNeeded(session, false);

        assertThat(session.getSummary()).isNull();
        verify(sessionRepository, never()).update(any());
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setLlmProvider("openai-responses");
        session.setLlmModel("model-a");
        return session;
    }

    private List<InterviewMessage> dialogMessages(int count) {
        List<InterviewMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(message(index % 2 == 0 ? "assistant" : "user", "dialog-" + index));
        }
        return messages;
    }

    private InterviewMessage message(String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
