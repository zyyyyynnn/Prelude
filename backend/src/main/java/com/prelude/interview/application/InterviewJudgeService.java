package com.prelude.interview.application;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.LlmSelection;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.llm.ChatPort;
import com.prelude.llm.ChatRequest;
import com.prelude.llm.LlmPurpose;
import com.prelude.llm.PromptIds;
import com.prelude.llm.PromptRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewJudgeService {

    private static final String ROLE_ASSISTANT = "assistant";
    private final InterviewMessageRepository interviewMessageRepository;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PromptRegistry promptRegistry;

    public Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMsg) {
        Long userId = session.getUserId();
        String lockKey = "lock:judge:" + userId + ":" + session.getId();
        boolean lockAcquired = false;
        try {
            lockAcquired = acquireJudgeLock(lockKey);
            if (!lockAcquired) {
                log.warn("Failed to acquire judge lock for user {}, skipping judge", userId);
                return Optional.empty();
            }

            JudgeResult result = resolveJudgeResult(session, userMsg);
            userMsg.setScore(result.score());
            userMsg.setHint(result.hint());
            interviewMessageRepository.update(userMsg);
            return Optional.of(result);
        } catch (Exception exception) {
            log.warn("Failed to update message with score/hint", exception);
            return Optional.empty();
        } finally {
            if (lockAcquired) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    private boolean acquireJudgeLock(String lockKey) {
        for (int retry = 0; retry < 10; retry++) {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private JudgeResult resolveJudgeResult(InterviewSession session, InterviewMessage userMsg) throws JacksonException {
        String questionContent = lastAssistantQuestion(session.getId(), userMsg);
        String systemPrompt = promptRegistry.load(PromptIds.JUDGE);
        String userPrompt = "面试岗位：" + session.getTargetPosition() + "\n" +
            "面试官提出的问题：" + questionContent + "\n" +
            "候选人的回答：" + userMsg.getContent() + "\n";

        String judgeOutput = chatPort.complete(ChatRequest.snapshot(
            session.getUserId(),
            LlmPurpose.JUDGE,
            PromptIds.JUDGE,
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
            Map.of("response_format", Map.of("type", "json_object"))
        ));
        return parseJudgeOutput(judgeOutput);
    }

    private JudgeResult parseJudgeOutput(String judgeOutput) throws JacksonException {
        String trimmed = stripJsonFence(judgeOutput);
        Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<>() {
        });
        Object scoreValue = map.get("score");
        if (!(scoreValue instanceof Number number)) {
            throw new IllegalArgumentException("Judge output must contain a numeric score");
        }
        double numericScore = number.doubleValue();
        if (!Double.isFinite(numericScore) || numericScore != Math.rint(numericScore)) {
            throw new IllegalArgumentException("Judge score must be a finite integer");
        }
        int score = (int) Math.max(1, Math.min(10, numericScore));
        Object hintValue = map.get("hint");
        if (hintValue != null && !(hintValue instanceof String)) {
            throw new IllegalArgumentException("Judge hint must be text");
        }
        String hint = hintValue == null || ((String) hintValue).isBlank()
            ? null
            : ((String) hintValue).trim();
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        normalized.put("score", score);
        if (hint != null) {
            normalized.put("hint", hint);
        }
        return new JudgeResult(score, hint, objectMapper.writeValueAsString(normalized));
    }

    private String lastAssistantQuestion(Long sessionId, InterviewMessage userMsg) {
        List<InterviewMessage> allMessages = interviewMessageRepository.listBySession(sessionId);
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            InterviewMessage message = allMessages.get(i);
            if (ROLE_ASSISTANT.equals(message.getRole()) && message.getSeqNum() < userMsg.getSeqNum()) {
                return message.getContent();
            }
        }
        return "";
    }

    private String stripJsonFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public record JudgeResult(int score, String hint, String json) {
    }
}
