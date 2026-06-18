package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.service.DevFixtureService;
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

    private final InterviewMessageMapper interviewMessageMapper;
    private final LlmRouter llmRouter;
    private final DevFixtureService devFixtureService;
    private final ObjectMapper objectMapper;
    private final InterviewStageManager interviewStageManager;
    private final StringRedisTemplate stringRedisTemplate;

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
            interviewMessageMapper.updateById(userMsg);
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
        String systemPrompt = """
            你是严谨的面试评估官。请针对当前的技术面试问题和候选人的回答，给出 1 到 10 之间的评分（1-10 整数）和一句简短的改进建议或评价（字数控制在 50 字以内）。
            必须只返回如下严格 JSON，不要输出 Markdown 代码围栏：
            {
              "score": 评分数字,
              "hint": "改进建议或评价"
            }
            """;
        String userPrompt = "面试岗位：" + session.getTargetPosition() + "\n" +
            "面试官提出的问题：" + questionContent + "\n" +
            "候选人的回答：" + userMsg.getContent() + "\n";

        String judgeOutput = llmRouter.chatWithSnapshot(
            session.getLlmProvider(),
            session.getLlmModel(),
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            Map.of("response_format", Map.of("type", "json_object"))
        );
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
        List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            InterviewMessage message = allMessages.get(i);
            if (ROLE_ASSISTANT.equals(message.getRole()) && message.getSeqNum() < userMsg.getSeqNum()) {
                return message.getContent();
            }
        }
        return "";
    }

    private int nextSeqNum(Long sessionId) {
        Long count = interviewMessageMapper.selectCount(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId));
        return count == null ? 0 : count.intValue();
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
