package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.LlmResponseText;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewJudgeService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final int MAX_HINT_LENGTH = 255;
    private static final Duration JUDGE_LOCK_LEASE = Duration.ofMinutes(5);
    private static final RedisScript<Long> RELEASE_LOCK = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final InterviewMessageRepository interviewMessageRepository;
    private final ModelExecutionPort llmPort;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PromptRegistry promptRegistry;

    public JudgeOutcome judgeAndPersist(InterviewSession session, InterviewMessage userMessage) {
        if (session == null || session.getId() == null || userMessage == null) {
            throw BusinessException.notFound("面试消息不存在");
        }
        if (!session.getId().equals(userMessage.getSessionId()) || !ROLE_USER.equals(userMessage.getRole())) {
            throw BusinessException.badRequest("面试消息不属于当前会话或不是候选人回答");
        }
        if (userMessage.getSupersededByMessageId() != null) {
            return JudgeOutcome.superseded();
        }
        JudgeOutcome terminal = terminalOutcome(userMessage);
        if (terminal != null) {
            return terminal;
        }

        String lockKey = "lock:judge:" + userMessage.getId();
        String lockToken = acquireJudgeLock(lockKey);
        if (lockToken == null) {
            log.warn("Judge is already running for message {}", userMessage.getId());
            return JudgeOutcome.busy();
        }
        try {
            InterviewMessage current = interviewMessageRepository.findById(userMessage.getId());
            if (current == null) {
                throw BusinessException.notFound("面试消息不存在");
            }
            if (!session.getId().equals(current.getSessionId()) || !ROLE_USER.equals(current.getRole())) {
                throw BusinessException.badRequest("面试消息不属于当前会话或不是候选人回答");
            }
            if (current.getSupersededByMessageId() != null) {
                return JudgeOutcome.superseded();
            }
            terminal = terminalOutcome(current);
            if (terminal != null) {
                return terminal;
            }
            final JudgeResult result;
            try {
                result = resolveJudgeResult(session, current);
            } catch (Exception exception) {
                String error = failureMessage(exception);
                log.warn("Judge failed for message {}: {}", current.getId(), error);
                return persistAssessment(current, null, error);
            }
            return persistAssessment(current, result, null);
        } finally {
            releaseJudgeLock(lockKey, lockToken);
        }
    }

    private String acquireJudgeLock(String lockKey) {
        String token = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, token, JUDGE_LOCK_LEASE)) ? token : null;
    }

    private void releaseJudgeLock(String lockKey, String token) {
        stringRedisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token);
    }

    private JudgeResult resolveJudgeResult(InterviewSession session, InterviewMessage userMessage) {
        if (session.getModelExecutionSnapshotId() == null
            || session.getTargetPosition() == null || session.getTargetPosition().isBlank()
            || userMessage.getContent() == null || userMessage.getContent().isBlank()) {
            throw new IllegalArgumentException("BUSINESS_FAILURE: incomplete interview or answer context");
        }
        String questionContent = lastAssistantQuestion(session.getId(), userMessage);
        if (questionContent == null || questionContent.isBlank()) {
            throw new IllegalArgumentException("EVIDENCE_FAILURE: missing preceding interviewer question");
        }
        String systemPrompt = promptRegistry.load(PromptIds.JUDGE);
        String userPrompt = "面试岗位：" + session.getTargetPosition() + "\n" +
            "面试官提出的问题：" + questionContent + "\n" +
            "候选人的回答：" + userMessage.getContent() + "\n";

        ModelExecutionPort.CompletionResult completion = llmPort.complete(
            new ModelExecutionPort.ModelExecutionRequest(
                session.getModelExecutionSnapshotId(),
                "judge",
                PromptIds.JUDGE,
                ModelExecutionPort.ResponseMode.JSON_OBJECT,
                List.of(
                    new ModelExecutionPort.Message("system", systemPrompt),
                    new ModelExecutionPort.Message("user", userPrompt)
                ),
                List.of(),
                List.of()
            ));
        return parseJudgeOutput(completion.content());
    }

    private JudgeResult parseJudgeOutput(String judgeOutput) {
        if (judgeOutput == null || judgeOutput.isBlank()) {
            throw new IllegalArgumentException("STRUCTURE_FAILURE: judge output is empty");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(
                LlmResponseText.stripJsonFence(judgeOutput), new tools.jackson.core.type.TypeReference<>() {
                });
            Object scoreValue = map.get("score");
            if (!(scoreValue instanceof Byte || scoreValue instanceof Short
                || scoreValue instanceof Integer || scoreValue instanceof Long || scoreValue instanceof BigInteger)) {
                throw new IllegalArgumentException("STRUCTURE_FAILURE: score must be an integer");
            }
            int score = new BigInteger(scoreValue.toString()).intValueExact();
            if (score < 1 || score > 10) {
                throw new IllegalArgumentException("STRUCTURE_FAILURE: score must be between 1 and 10");
            }
            Object hintValue = map.get("hint");
            if (hintValue != null && !(hintValue instanceof String)) {
                throw new IllegalArgumentException("STRUCTURE_FAILURE: hint must be text");
            }
            String hint = hintValue == null ? null : ((String) hintValue).trim();
            if (hint != null && hint.isBlank()) {
                hint = null;
            }
            if (hint != null && hint.length() > MAX_HINT_LENGTH) {
                throw new IllegalArgumentException("STRUCTURE_FAILURE: hint exceeds " + MAX_HINT_LENGTH + " characters");
            }
            return toJudgeResult(score, hint);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("STRUCTURE_FAILURE: invalid judge output", exception);
        }
    }

    private JudgeOutcome persistAssessment(InterviewMessage userMessage, JudgeResult result, String error) {
        InterviewMessage update = new InterviewMessage();
        update.setId(userMessage.getId());
        update.setScore(result == null ? null : result.score());
        update.setHint(result == null ? null : result.hint());
        update.setAssessmentStatus(result == null ? AssessmentStatus.UNSCORED : AssessmentStatus.SCORED);
        update.setAssessmentError(error);
        if (interviewMessageRepository.updateAssessmentIfPending(update) == 1) {
            userMessage.setScore(update.getScore());
            userMessage.setHint(update.getHint());
            userMessage.setAssessmentStatus(update.getAssessmentStatus());
            userMessage.setAssessmentError(update.getAssessmentError());
            return result == null ? JudgeOutcome.unscored(error) : JudgeOutcome.completed(result);
        }
        InterviewMessage current = interviewMessageRepository.findById(userMessage.getId());
        JudgeOutcome terminal = terminalOutcome(current);
        if (terminal != null) {
            return terminal;
        }
        if (current != null && current.getSupersededByMessageId() != null) {
            return JudgeOutcome.superseded();
        }
        throw new IllegalStateException("Failed to persist assessment for message " + userMessage.getId());
    }

    private JudgeOutcome terminalOutcome(InterviewMessage message) {
        if (message == null || message.getAssessmentStatus() == null) {
            return null;
        }
        return switch (message.getAssessmentStatus()) {
            case SCORED -> {
                if (message.getScore() == null) {
                    throw new IllegalStateException("SCORED assessment is missing its score for message " + message.getId());
                }
                yield JudgeOutcome.completed(toJudgeResult(message));
            }
            case UNSCORED -> JudgeOutcome.unscored(message.getAssessmentError());
            case PENDING -> null;
        };
    }

    private JudgeResult toJudgeResult(InterviewMessage message) {
        return toJudgeResult(message.getScore(), message.getHint());
    }

    private JudgeResult toJudgeResult(int score, String hint) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("score", score);
        if (hint != null && !hint.isBlank()) {
            normalized.put("hint", hint);
        }
        try {
            return new JudgeResult(score, hint, objectMapper.writeValueAsString(normalized));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Failed to serialize judge result", exception);
        }
    }

    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }

    private String lastAssistantQuestion(Long sessionId, InterviewMessage userMessage) {
        if (userMessage.getSeqNum() == null) {
            return null;
        }
        List<InterviewMessage> messages = InterviewMessage.effectiveMessages(interviewMessageRepository.listBySession(sessionId));
        for (int index = messages.size() - 1; index >= 0; index--) {
            InterviewMessage message = messages.get(index);
            if (ROLE_ASSISTANT.equals(message.getRole()) && message.getSeqNum() != null
                && message.getSeqNum() < userMessage.getSeqNum()) {
                return message.getContent();
            }
        }
        return null;
    }
}
