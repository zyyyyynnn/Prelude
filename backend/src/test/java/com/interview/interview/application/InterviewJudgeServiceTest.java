package com.interview.interview.application;

import com.interview.interview.application.InterviewJudgeService;
import com.interview.interview.application.InterviewStageManager;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.PromptRegistry;
import com.interview.interview.infrastructure.persistence.InterviewMessageMapper;
import com.interview.interview.application.port.InterviewFixturePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewJudgeServiceTest {

    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private ChatPort chatPort;
    @Mock private InterviewFixturePort devFixtureService;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PromptRegistry promptRegistry;

    private InterviewJudgeService service;

    @BeforeEach
    void setUp() {
        service = new InterviewJudgeService(
            interviewMessageMapper,
            chatPort,
            devFixtureService,
            new ObjectMapper(),
            interviewStageManager,
            stringRedisTemplate,
            promptRegistry
        );
    }

    @Test
    void nextSeqNumReturnsZeroWhenNoMessages() {
        when(interviewMessageMapper.findLatest(7L)).thenReturn(null);

        int result = service.nextSeqNum(7L);

        assertThat(result).isZero();
    }

    @Test
    void nextSeqNumReturnsZeroWhenLatestSeqNumIsNull() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(null);
        when(interviewMessageMapper.findLatest(7L)).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isZero();
    }

    @Test
    void nextSeqNumReturnsLatestPlusOne() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(4);
        when(interviewMessageMapper.findLatest(7L)).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void nextSeqNumHandlesSparseSequence() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(99);
        when(interviewMessageMapper.findLatest(7L)).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isEqualTo(100);
    }

    @Test
    void voiceFixturePersistsDeterministicScoreAndReleasesLock() {
        InterviewSession session = session();
        InterviewMessage userMessage = message("user", "回答", 2);
        InterviewMessage latest = message("assistant", "问题", 4);
        lockSucceeds();
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(interviewMessageMapper.findLatest(7L)).thenReturn(latest);

        Optional<InterviewJudgeService.JudgeResult> result = service.judgeAndPersist(session, userMessage, true);

        assertThat(result).hasValueSatisfying(judge -> {
            assertThat(judge.score()).isEqualTo(9);
            assertThat(judge.hint()).contains("语音表达流畅");
        });
        assertThat(userMessage.getScore()).isEqualTo(9);
        verify(interviewMessageMapper).update(userMessage);
        verify(stringRedisTemplate).delete("lock:judge:42:7");
    }

    @Test
    void textFixtureUsesCurrentStageAndReplyIndex() {
        InterviewSession session = session();
        InterviewMessage userMessage = message("user", "回答", 2);
        lockSucceeds();
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(interviewStageManager.currentStageName(7L)).thenReturn("technical");
        when(interviewStageManager.assistantRepliesInCurrentStage(7L)).thenReturn(3);
        when(devFixtureService.resolveMockJudge("technical", 3))
            .thenReturn("{\"score\":8,\"hint\":\"结构清晰\"}");

        Optional<InterviewJudgeService.JudgeResult> result = service.judgeAndPersist(session, userMessage, false);

        assertThat(result).hasValueSatisfying(judge -> {
            assertThat(judge.score()).isEqualTo(8);
            assertThat(judge.hint()).isEqualTo("结构清晰");
        });
        verify(interviewMessageMapper).update(userMessage);
    }

    @Test
    void productionJudgeUsesLastAssistantQuestionAndClampsFencedScore() {
        InterviewSession session = session();
        InterviewMessage question = message("assistant", "请解释 JVM", 1);
        InterviewMessage userMessage = message("user", "堆和栈", 2);
        lockSucceeds();
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(interviewMessageMapper.listBySession(7L)).thenReturn(List.of(question, userMessage));
        when(promptRegistry.load("interview.judge", "v1")).thenReturn("judge system prompt");
        when(chatPort.complete(any())).thenReturn("```json\n{\"score\":15,\"hint\":\"覆盖完整\"}\n```");

        Optional<InterviewJudgeService.JudgeResult> result = service.judgeAndPersist(session, userMessage, false);

        assertThat(result).hasValueSatisfying(judge -> {
            assertThat(judge.score()).isEqualTo(10);
            assertThat(judge.hint()).isEqualTo("覆盖完整");
        });
        org.mockito.ArgumentCaptor<com.interview.platform.llm.ChatRequest> requestCaptor =
            org.mockito.ArgumentCaptor.forClass(com.interview.platform.llm.ChatRequest.class);
        verify(chatPort).complete(requestCaptor.capture());
        assertThat(requestCaptor.getValue().messages().get(1).get("content"))
            .contains("请解释 JVM", "堆和栈");
    }

    @Test
    void malformedProductionOutputFallsBackToDefaultJudgeResult() {
        InterviewSession session = session();
        InterviewMessage userMessage = message("user", "回答", 1);
        lockSucceeds();
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(interviewMessageMapper.listBySession(7L)).thenReturn(List.of(userMessage));
        when(promptRegistry.load("interview.judge", "v1")).thenReturn("judge system prompt");
        when(chatPort.complete(any())).thenReturn("not-json");

        Optional<InterviewJudgeService.JudgeResult> result = service.judgeAndPersist(session, userMessage, false);

        assertThat(result).hasValueSatisfying(judge -> {
            assertThat(judge.score()).isEqualTo(7);
            assertThat(judge.hint()).contains("继续加油");
        });
    }

    @Test
    void interruptedLockWaitSkipsJudgeWithoutMutation() {
        InterviewSession session = session();
        InterviewMessage userMessage = message("user", "回答", 1);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        Thread.currentThread().interrupt();
        try {
            assertThat(service.judgeAndPersist(session, userMessage, false)).isEmpty();
        } finally {
            Thread.interrupted();
        }

        verify(interviewMessageMapper, never()).update(any(InterviewMessage.class));
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    private void lockSucceeds() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setUserId(42L);
        session.setTargetPosition("Java 开发");
        session.setLlmProvider("openai-compatible");
        session.setLlmModel("model-a");
        return session;
    }

    private InterviewMessage message(String role, String content, int seqNum) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(7L);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        return message;
    }
}
