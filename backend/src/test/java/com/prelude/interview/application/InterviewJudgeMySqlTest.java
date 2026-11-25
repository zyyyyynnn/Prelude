package com.prelude.interview.application;

import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import com.prelude.test.InterviewDataFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.interview.judge.retry.enabled=false",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
class InterviewJudgeMySqlTest {

    @MockitoBean
    private LlmPort llm;

    @MockitoBean
    private PromptRegistry prompts;

    @Autowired
    private InterviewMessageRepository messages;

    @Autowired
    private InterviewSessionRepository sessions;

    @Autowired
    private InterviewJudgeService judge;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparePrompt() {
        when(prompts.load(PromptIds.JUDGE)).thenReturn("judge prompt");
    }

    @Test
    void recoverySelectsOnlyOldPendingUserMessagesAndUsesTheFrozenSnapshot() {
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbc, "judge-recovery");
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        addMessage(sessionId, "assistant", "请介绍项目", 0, null, old);
        InterviewMessage answer = addMessage(sessionId, "user", "候选人回答", 1, AssessmentStatus.PENDING, old);
        addMessage(sessionId, "user", "刚刚回答", 2, AssessmentStatus.PENDING, LocalDateTime.now());
        addMessage(sessionId, "user", "已评分", 3, AssessmentStatus.SCORED, old);
        addMessage(sessionId, "user", "未评分", 4, AssessmentStatus.UNSCORED, old);
        addMessage(sessionId, "assistant", "后续问题", 5, null, old);
        when(llm.complete(any())).thenReturn(new LlmPort.CompletionResult("{\"score\":8,\"hint\":\"补充量化结果\"}", null));

        List<InterviewMessage> pending = messages.findPendingForJudge(LocalDateTime.now().minusMinutes(2), 100);
        assertThat(pending)
            .filteredOn(message -> message.getSessionId().equals(sessionId))
            .extracting(InterviewMessage::getId)
            .containsExactly(answer.getId());

        InterviewSession session = sessions.selectById(sessionId);
        JudgeOutcome outcome = judge.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.COMPLETED);
        assertThat(status(answer.getId())).isEqualTo("SCORED");
        ArgumentCaptor<LlmPort.ModelExecutionRequest> request = ArgumentCaptor.forClass(LlmPort.ModelExecutionRequest.class);
        verify(llm).complete(request.capture());
        assertThat(request.getValue().snapshotId()).isEqualTo(session.getModelExecutionSnapshotId());
        assertThat(request.getValue().purpose()).isEqualTo("judge");

        JudgeOutcome repeated = judge.judgeAndPersist(session, messages.findById(answer.getId()));
        assertThat(repeated.status()).isEqualTo(JudgeOutcome.Status.COMPLETED);
        assertThat(repeated.result().score()).isEqualTo(8);
        verify(llm, times(1)).complete(any());
    }

    @Test
    void anUnscoredAnswerIsNotSubmittedAgain() {
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbc, "judge-unscored");
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        addMessage(sessionId, "assistant", "请介绍项目", 0, null, old);
        InterviewMessage answer = addMessage(sessionId, "user", "候选人回答", 1, AssessmentStatus.PENDING, old);
        when(llm.complete(any())).thenThrow(new IllegalStateException("provider unavailable"));
        InterviewSession session = sessions.selectById(sessionId);

        JudgeOutcome first = judge.judgeAndPersist(session, answer);
        JudgeOutcome repeated = judge.judgeAndPersist(session, messages.findById(answer.getId()));

        assertThat(first.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(repeated.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(repeated.error()).isEqualTo("provider unavailable");
        assertThat(status(answer.getId())).isEqualTo("UNSCORED");
        verify(llm, times(1)).complete(any());
    }

    @Test
    void busyKeepsTheAnswerPendingWithoutCallingTheModel() {
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbc, "judge-busy");
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        addMessage(sessionId, "assistant", "请介绍项目", 0, null, old);
        InterviewMessage answer = addMessage(sessionId, "user", "候选人回答", 1, AssessmentStatus.PENDING, old);
        String lockKey = "lock:judge:" + answer.getId();
        redis.opsForValue().set(lockKey, "another-worker", Duration.ofMinutes(1));
        try {
            JudgeOutcome outcome = judge.judgeAndPersist(sessions.selectById(sessionId), answer);

            assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.BUSY);
            assertThat(status(answer.getId())).isEqualTo("PENDING");
            verifyNoInteractions(llm);
        } finally {
            redis.delete(lockKey);
        }
    }

    private InterviewMessage addMessage(long sessionId, String role, String content, int seq,
                                        AssessmentStatus status, LocalDateTime createdAt) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seq);
        message.setAssessmentStatus(status);
        message.setCreatedAt(createdAt);
        messages.add(message);
        return message;
    }

    private String status(long messageId) {
        return jdbc.queryForObject("SELECT assessment_status FROM interview_message WHERE id = ?", String.class, messageId);
    }
}
