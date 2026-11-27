package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.PromptRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewJudgeServiceTest {

    private final InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
    private final ModelExecutionPort llm = mock(ModelExecutionPort.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final PromptRegistry prompts = mock(PromptRegistry.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final InterviewJudgeService service = new InterviewJudgeService(
        messages, llm, new ObjectMapper(), redis, prompts);
    private final InterviewSession session = new InterviewSession();
    private final InterviewMessage answer = new InterviewMessage();

    @BeforeEach
    void setUp() {
        session.setId(7L);
        session.setAccountId(3L);
        session.setTargetPosition("Java 后端");
        session.setModelExecutionSnapshotId(5L);
        answer.setId(11L);
        answer.setSessionId(7L);
        answer.setRole("user");
        answer.setContent("候选人回答");
        answer.setSeqNum(2);
        answer.setAssessmentStatus(AssessmentStatus.PENDING);
        InterviewMessage question = new InterviewMessage();
        question.setSessionId(7L);
        question.setRole("assistant");
        question.setContent("请介绍项目");
        question.setSeqNum(1);
        when(messages.listBySession(7L)).thenReturn(List.of(question, answer));
        when(messages.findById(11L)).thenReturn(answer);
        when(messages.updateAssessmentIfPending(any())).thenReturn(1);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(prompts.load(anyString())).thenReturn("judge prompt");
    }

    @Test
    void completedAssessmentPersistsTheStrictResult() {
        when(llm.complete(any())).thenReturn(completion("{\"score\":8,\"hint\":\"补充量化结果\"}"));

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.COMPLETED);
        assertThat(outcome.result().score()).isEqualTo(8);
        assertThat(outcome.result().hint()).isEqualTo("补充量化结果");
        assertThat(answer.getAssessmentStatus()).isEqualTo(AssessmentStatus.SCORED);
        assertThat(answer.getAssessmentError()).isNull();
        verify(values, times(1)).setIfAbsent(eq("lock:judge:11"), anyString(), any(Duration.class));
        verify(messages).updateAssessmentIfPending(any());
    }

    @Test
    void busyAssessmentDoesNotRetryOrMutateTheAnswer() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.BUSY);
        assertThat(answer.getAssessmentStatus()).isEqualTo(AssessmentStatus.PENDING);
        verify(values, times(1)).setIfAbsent(eq("lock:judge:11"), anyString(), any(Duration.class));
        verifyNoInteractions(llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void modelFailurePersistsUnscoredWithoutADefaultScore() {
        when(llm.complete(any())).thenThrow(new IllegalStateException("provider unavailable"));

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).contains("provider unavailable");
        assertThat(answer.getAssessmentStatus()).isEqualTo(AssessmentStatus.UNSCORED);
        assertThat(answer.getScore()).isNull();
        assertThat(answer.getHint()).isNull();
        verify(messages).updateAssessmentIfPending(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-json",
        "{\"score\":0}",
        "{\"score\":11}",
        "{\"score\":8.5}",
        "{\"score\":8.0}",
        "{\"score\":\"8\"}",
        "{\"score\":8,\"hint\":42}"
    })
    void invalidStructurePersistsUnscored(String output) {
        when(llm.complete(any())).thenReturn(completion(output));

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).contains("STRUCTURE_FAILURE");
        assertThat(answer.getScore()).isNull();
        assertThat(answer.getAssessmentStatus()).isEqualTo(AssessmentStatus.UNSCORED);
    }

    @Test
    void oversizedHintPersistsUnscored() {
        when(llm.complete(any())).thenReturn(completion(
            "{\"score\":8,\"hint\":\"" + "x".repeat(256) + "\"}"));

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).contains("hint exceeds 255");
        assertThat(answer.getScore()).isNull();
    }

    @Test
    void incompleteBusinessContextPersistsUnscored() {
        session.setModelExecutionSnapshotId(null);

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).contains("BUSINESS_FAILURE");
        verifyNoInteractions(llm);
        verify(messages).updateAssessmentIfPending(any());
    }

    @Test
    void missingQuestionEvidencePersistsUnscoredWithoutCallingTheModel() {
        when(messages.listBySession(7L)).thenReturn(List.of(answer));

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).contains("EVIDENCE_FAILURE");
        verifyNoInteractions(llm);
        verify(messages).updateAssessmentIfPending(any());
    }

    @Test
    void rejectsMessagesFromAnotherSession() {
        answer.setSessionId(8L);

        assertThatThrownBy(() -> service.judgeAndPersist(session, answer))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前会话");
        verifyNoInteractions(redis, llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void rejectsNonUserMessages() {
        answer.setRole("assistant");

        assertThatThrownBy(() -> service.judgeAndPersist(session, answer))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不是候选人回答");
        verifyNoInteractions(redis, llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void reanswerJudgingIgnoresTheSupersededAnswersFollowUpQuestion() {
        InterviewMessage question = new InterviewMessage();
        question.setId(8L);
        question.setSessionId(7L);
        question.setRole("assistant");
        question.setContent("原始问题");
        question.setSeqNum(1);
        InterviewMessage oldAnswer = new InterviewMessage();
        oldAnswer.setId(10L);
        oldAnswer.setSessionId(7L);
        oldAnswer.setRole("user");
        oldAnswer.setContent("旧回答");
        oldAnswer.setSeqNum(2);
        oldAnswer.setSupersededByMessageId(11L);
        InterviewMessage oldFollowUp = new InterviewMessage();
        oldFollowUp.setId(9L);
        oldFollowUp.setSessionId(7L);
        oldFollowUp.setRole("assistant");
        oldFollowUp.setContent("旧回答的追问");
        oldFollowUp.setSeqNum(3);
        oldFollowUp.setReplyToMessageId(10L);
        answer.setSeqNum(4);
        when(messages.listBySession(7L)).thenReturn(List.of(question, oldAnswer, oldFollowUp, answer));
        when(llm.complete(any())).thenReturn(completion("{\"score\":8}"));

        service.judgeAndPersist(session, answer);

        ArgumentCaptor<ModelExecutionPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(ModelExecutionPort.ModelExecutionRequest.class);
        verify(llm).complete(request.capture());
        String prompt = request.getValue().messages().get(1).content();
        assertThat(prompt).contains("原始问题").doesNotContain("旧回答的追问");
    }

    @Test
    void aSupersededAnswerIsNotSubmittedToTheModel() {
        answer.setSupersededByMessageId(12L);

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.SUPERSEDED);
        verifyNoInteractions(redis, llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void anAnswerSupersededDuringJudgingCannotPersistItsAssessment() {
        when(llm.complete(any())).thenAnswer(invocation -> {
            answer.setSupersededByMessageId(12L);
            return completion("{\"score\":8}");
        });
        when(messages.updateAssessmentIfPending(any())).thenReturn(0);

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.SUPERSEDED);
        assertThat(answer.getAssessmentStatus()).isEqualTo(AssessmentStatus.PENDING);
        verify(messages).updateAssessmentIfPending(any());
    }

    @Test
    void completedPersistenceFailurePropagates() {
        when(llm.complete(any())).thenReturn(completion("{\"score\":8}"));
        when(messages.updateAssessmentIfPending(any())).thenReturn(0);

        assertThatThrownBy(() -> service.judgeAndPersist(session, answer))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to persist assessment");
    }

    @Test
    void unscoredPersistenceFailurePropagates() {
        when(llm.complete(any())).thenThrow(new IllegalStateException("provider unavailable"));
        when(messages.updateAssessmentIfPending(any())).thenReturn(0);

        assertThatThrownBy(() -> service.judgeAndPersist(session, answer))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to persist assessment");
    }

    @Test
    void aTerminalStoredAssessmentIsNotSubmittedAgain() {
        InterviewMessage stored = new InterviewMessage();
        stored.setId(11L);
        stored.setSessionId(7L);
        stored.setRole("user");
        stored.setAssessmentStatus(AssessmentStatus.UNSCORED);
        stored.setAssessmentError("provider unavailable");
        when(messages.findById(11L)).thenReturn(stored);

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).isEqualTo("provider unavailable");
        verifyNoInteractions(llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void anAlreadyUnscoredAnswerIsNotSubmittedAgain() {
        answer.setAssessmentStatus(AssessmentStatus.UNSCORED);
        answer.setAssessmentError("invalid judge output");

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.UNSCORED);
        assertThat(outcome.error()).isEqualTo("invalid judge output");
        verifyNoInteractions(redis, llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    @Test
    void anAlreadyScoredAnswerIsNotSubmittedAgain() {
        answer.setAssessmentStatus(AssessmentStatus.SCORED);
        answer.setScore(9);
        answer.setHint("existing");

        JudgeOutcome outcome = service.judgeAndPersist(session, answer);

        assertThat(outcome.status()).isEqualTo(JudgeOutcome.Status.COMPLETED);
        assertThat(outcome.result().score()).isEqualTo(9);
        verifyNoInteractions(redis, llm);
        verify(messages, never()).updateAssessmentIfPending(any());
    }

    private ModelExecutionPort.CompletionResult completion(String content) {
        return new ModelExecutionPort.CompletionResult(content, null);
    }
}
