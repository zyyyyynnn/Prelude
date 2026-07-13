package com.interview.interview.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.LlmSelection;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.LlmPurpose;
import com.interview.platform.llm.PromptVersions;
import com.interview.platform.llm.PromptRegistry;
import com.interview.interview.application.port.InterviewFixturePort;
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
    private static final String FALLBACK_JUDGE_JSON = "{\"score\": 7, \"hint\": \"回答已记录，继续加油。\"}";

    private final InterviewMessageRepository interviewMessageRepository;
    private final ChatPort chatPort;
    private final InterviewFixturePort devFixtureService;
    private final ObjectMapper objectMapper;
    private final InterviewStageManager interviewStageManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final PromptRegistry promptRegistry;

    public Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMsg, boolean voiceMode) {
        Long userId = session.getUserId();
        String lockKey = "lock:judge:" + userId + ":" + session.getId();
        boolean lockAcquired = false;
        try {
            lockAcquired = acquireJudgeLock(lockKey);
            if (!lockAcquired) {
                log.warn("Failed to acquire judge lock for user {}, skipping judge", userId);
                return Optional.empty();
            }

            String judgeResultJson = resolveJudgeJson(session, userMsg, voiceMode);
            JudgeResult result = parseJudgeJson(judgeResultJson);
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

    private String resolveJudgeJson(InterviewSession session, InterviewMessage userMsg, boolean voiceMode) throws JsonProcessingException {
        if (devFixtureService != null && devFixtureService.isEnabled()) {
            if (voiceMode) {
                int replyIndex = nextSeqNum(session.getId());
                int score = 7 + (replyIndex % 3);
                return objectMapper.writeValueAsString(Map.of("score", score, "hint", "语音表达流畅，内容符合技术规范要求。"));
            }
            String currentStage = interviewStageManager.currentStageName(session.getId());
            int replyIndex = interviewStageManager.assistantRepliesInCurrentStage(session.getId());
            return devFixtureService.resolveMockJudge(currentStage, replyIndex);
        }

        String questionContent = lastAssistantQuestion(session.getId(), userMsg);
        String systemPrompt = promptRegistry.load(PromptVersions.JUDGE, PromptVersions.V1);
        String userPrompt = "面试岗位：" + session.getTargetPosition() + "\n" +
            "面试官提出的问题：" + questionContent + "\n" +
            "候选人的回答：" + userMsg.getContent() + "\n";

        String judgeOutput = chatPort.complete(ChatRequest.snapshot(
            session.getUserId(),
            LlmPurpose.JUDGE,
            PromptVersions.JUDGE,
            PromptVersions.V1,
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
            Map.of("response_format", Map.of("type", "json_object"))
        ));
        return normalizeJudgeOutput(judgeOutput);
    }

    private String normalizeJudgeOutput(String judgeOutput) {
        String trimmed = stripJsonFence(judgeOutput);
        try {
            Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<>() {
            });
            int score = ((Number) map.getOrDefault("score", 7)).intValue();
            int safeScore = Math.max(1, Math.min(10, score));
            String hint = (String) map.getOrDefault("hint", "回答已记录");
            return objectMapper.writeValueAsString(Map.of("score", safeScore, "hint", hint));
        } catch (Exception exception) {
            log.warn("Failed to parse judge output: {}", judgeOutput, exception);
            return FALLBACK_JUDGE_JSON;
        }
    }

    private JudgeResult parseJudgeJson(String judgeResultJson) throws JsonProcessingException {
        Map<String, Object> parsedMap = objectMapper.readValue(judgeResultJson, new TypeReference<>() {
        });
        int score = ((Number) parsedMap.get("score")).intValue();
        String hint = (String) parsedMap.get("hint");
        return new JudgeResult(score, hint, judgeResultJson);
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

    int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageRepository.findLatest(sessionId);
        if (latest == null || latest.getSeqNum() == null) {
            return 0;
        }
        return latest.getSeqNum() + 1;
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
