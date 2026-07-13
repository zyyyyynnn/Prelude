package com.interview.interview.application;

import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.interview.application.port.InterviewSessionRepository;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.retrieval.RetrievalPort;
import com.interview.resume.api.port.ResumeContextPort;
import com.interview.resume.api.port.ResumeProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewContextServiceTest {

    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private InterviewMessageRepository messageRepository;
    @Mock private ResumeContextPort resumeContextPort;
    @Mock private RetrievalPort retrievalPort;
    @Mock private InterviewStageManager stageManager;

    private InterviewContextService service;

    @BeforeEach
    void setUp() {
        service = new InterviewContextService(
            sessionRepository,
            messageRepository,
            resumeContextPort,
            retrievalPort,
            stageManager
        );
    }

    @Test
    void summaryWindowKeepsSystemContextRagAndLastEightDialogMessages() {
        InterviewSession session = session("Java 开发", "此前摘要");
        List<InterviewMessage> messages = new ArrayList<>();
        messages.add(message("system", "基础系统提示"));
        for (int index = 0; index < 10; index++) {
            messages.add(message(index % 2 == 0 ? "user" : "assistant", "dialog-" + index));
        }
        when(sessionRepository.selectById(7L)).thenReturn(session);
        when(messageRepository.listBySession(7L)).thenReturn(messages);
        when(retrievalPort.search(RetrievalPort.SCOPE_SESSION, 7L, "dialog-8", 5))
            .thenReturn(List.of("简历片段", "岗位片段"));

        List<Map<String, String>> result = service.buildContextMessages(7L);

        assertThat(result).hasSize(11);
        assertThat(result.get(0)).containsEntry("role", "system").containsEntry("content", "基础系统提示");
        assertThat(result.get(1).get("content")).contains("[1] 简历片段", "[2] 岗位片段");
        assertThat(result.get(2).get("content")).contains("此前摘要");
        assertThat(result.subList(3, result.size()))
            .extracting(item -> item.get("content"))
            .containsExactly("dialog-2", "dialog-3", "dialog-4", "dialog-5",
                "dialog-6", "dialog-7", "dialog-8", "dialog-9");
    }

    @Test
    void recentWindowKeepsAllSystemMessagesAndOnlyLastTwelveDialogMessages() {
        InterviewSession session = session("Java 开发", null);
        List<InterviewMessage> messages = new ArrayList<>();
        messages.add(message("system", "system-1"));
        messages.add(message("system", "system-2"));
        for (int index = 0; index < 14; index++) {
            messages.add(message(index % 2 == 0 ? "user" : "assistant", "dialog-" + index));
        }
        when(sessionRepository.selectById(7L)).thenReturn(session);
        when(messageRepository.listBySession(7L)).thenReturn(messages);
        when(retrievalPort.search(RetrievalPort.SCOPE_SESSION, 7L, "dialog-12", 5)).thenReturn(List.of());

        List<Map<String, String>> result = service.buildContextMessages(7L);

        assertThat(result).hasSize(14);
        assertThat(result).extracting(item -> item.get("content"))
            .containsExactly("system-1", "system-2", "dialog-2", "dialog-3", "dialog-4", "dialog-5",
                "dialog-6", "dialog-7", "dialog-8", "dialog-9", "dialog-10", "dialog-11",
                "dialog-12", "dialog-13");
    }

    @Test
    void autoStartPromptIncludesOwnedResumeStageAndBoundedResumeText() {
        InterviewSession session = session("Java 开发", null);
        session.setResumeId(5L);
        String resumeText = "x".repeat(1801);
        ResumeProjection resume = new ResumeProjection(5L, 42L, "resume.pdf", resumeText, List.of(), List.of(), 1);
        when(resumeContextPort.requireOwnedProjection(42L, 5L)).thenReturn(resume);
        when(sessionRepository.selectById(7L)).thenReturn(session);
        when(messageRepository.listBySession(7L)).thenReturn(List.of());
        when(retrievalPort.search(RetrievalPort.SCOPE_SESSION, 7L, "Java 开发", 5)).thenReturn(List.of());
        when(stageManager.currentStageName(7L)).thenReturn("technical");

        List<Map<String, String>> result = service.buildAutoStartMessages(session);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).containsEntry("role", "user");
        assertThat(result.getFirst().get("content"))
            .contains("目标岗位：Java 开发", "当前阶段：technical", "候选人简历文件名：resume.pdf")
            .contains("x".repeat(1800) + "...")
            .endsWith("要求：只输出第一条面试问题，不要附加解释。");
    }

    @Test
    void missingSessionAndMessagesProducesEmptyContextWithoutRetrieval() {
        when(sessionRepository.selectById(7L)).thenReturn(null);
        when(messageRepository.listBySession(7L)).thenReturn(List.of());

        assertThat(service.buildContextMessages(7L)).isEmpty();
        verify(retrievalPort, never()).search(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt()
        );
    }

    private InterviewSession session(String targetPosition, String summary) {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setTargetPosition(targetPosition);
        session.setSummary(summary);
        return session;
    }

    private InterviewMessage message(String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
