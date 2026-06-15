package com.interview.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.UserContext;
import com.interview.config.DevFixtureProperties;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.service.SessionRagService;
import com.interview.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private final VoiceService voiceService;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final SessionRagService sessionRagService;
    private final DevFixtureProperties devFixtureProperties;
    
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";

    // Session states
    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> activeSessionIds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("WebSocket connection rejected: userId not bound in session attributes");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionBuffers.put(session.getId(), new ByteArrayOutputStream());
        log.info("WebSocket connection established for user {}, connection id: {}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionBuffers.remove(session.getId());
        activeSessionIds.remove(session.getId());
        log.info("WebSocket connection closed, connection id: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
        if (buffer != null) {
            byte[] bytes = message.getPayload().array();
            buffer.write(bytes);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String payload = message.getPayload();
        Map<String, Object> requestMap;
        try {
            requestMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("WebSocket parse text payload failed: {}", payload);
            return;
        }

        String type = (String) requestMap.get("type");
        if ("start".equalsIgnoreCase(type)) {
            Number sessionIdNum = (Number) requestMap.get("sessionId");
            if (sessionIdNum != null) {
                activeSessionIds.put(session.getId(), sessionIdNum.longValue());
                ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
                if (buffer != null) {
                    buffer.reset(); // Reset audio accumulator for a new recording
                }
                log.info("Started voice session {} on connection {}", sessionIdNum, session.getId());
            }
        } else if ("stop".equalsIgnoreCase(type)) {
            Long activeSessionId = activeSessionIds.get(session.getId());
            if (activeSessionId == null) {
                sendJson(session, Map.of("type", "error", "message", "面试会话未初始化"));
                return;
            }

            ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
            if (buffer == null || buffer.size() == 0) {
                sendJson(session, Map.of("type", "error", "message", "没有检测到任何音频数据"));
                return;
            }

            byte[] audioBytes = buffer.toByteArray();
            buffer.reset(); // Clear buffer for next turn

            // Run processing chain asynchronously to prevent blocking WS connection thread
            sseTaskExecutor.execute(() -> {
                UserContext.setCurrentUserId(userId);
                try {
                    // 1. Tell client that we are transcribing speech to text
                    sendJson(session, Map.of("type", "status", "status", "stt_processing"));

                    String transcribed = voiceService.speechToText(activeSessionId, audioBytes, "voice.webm");
                    if (transcribed == null || transcribed.trim().isEmpty()) {
                        sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                        return;
                    }

                    // 2. Persist user message to database
                    InterviewSession interviewSession = interviewSessionMapper.selectById(activeSessionId);
                    InterviewMessage userMsg = new InterviewMessage();
                    userMsg.setSessionId(activeSessionId);
                    userMsg.setRole(ROLE_USER);
                    userMsg.setContent(transcribed);
                    userMsg.setSeqNum(nextSeqNum(activeSessionId));
                    interviewMessageMapper.insert(userMsg);

                    // Send recognized text to display immediately in user chat bubble
                    sendJson(session, Map.of("type", "user_text", "text", transcribed));

                    // 3. Start streaming and synthesizing responses
                    sendJson(session, Map.of("type", "status", "status", "tts_processing"));

                    List<Map<String, String>> contextMessages = buildContextMessages(activeSessionId);

                    StringBuilder assistantReply = new StringBuilder();
                    StringBuilder sentenceBuilder = new StringBuilder();

                    // Executor queue to keep TTS chunks sequentially played in order
                    ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();
                    AtomicBoolean ttsFailed = new AtomicBoolean(false);

                    llmRouter.streamWithSnapshot(
                        interviewSession.getLlmProvider(),
                        interviewSession.getLlmModel(),
                        contextMessages,
                        delta -> {
                            // a. Stream text chunk to frontend for live subtitle rendering
                            sendJson(session, Map.of("type", "text", "chunk", delta));

                            assistantReply.append(delta);
                            sentenceBuilder.append(delta);

                            // b. Check sentence boundary
                            String sentence = extractSentenceIfComplete(sentenceBuilder);
                            if (sentence != null && !sentence.trim().isEmpty() && !ttsFailed.get()) {
                                ttsExecutor.submit(() -> {
                                    if (ttsFailed.get()) return;
                                    try {
                                        byte[] speechBytes = voiceService.textToSpeech(sentence);
                                        if (speechBytes != null && speechBytes.length > 0) {
                                            String base64 = Base64.getEncoder().encodeToString(speechBytes);
                                            sendJson(session, Map.of("type", "audio", "data", base64));
                                        }
                                    } catch (Exception e) {
                                        log.error("TTS generation failed, fallback to text-only: {}", e.getMessage());
                                        ttsFailed.set(true);
                                        sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                                    }
                                });
                            }
                        }
                    );

                    // Handle remaining sentence piece after LLM stream completes
                    String remaining = sentenceBuilder.toString();
                    if (!remaining.trim().isEmpty() && !ttsFailed.get()) {
                        ttsExecutor.submit(() -> {
                            if (ttsFailed.get()) return;
                            try {
                                byte[] speechBytes = voiceService.textToSpeech(remaining);
                                if (speechBytes != null && speechBytes.length > 0) {
                                    String base64 = Base64.getEncoder().encodeToString(speechBytes);
                                    sendJson(session, Map.of("type", "audio", "data", base64));
                                }
                            } catch (Exception e) {
                                log.error("TTS last generation failed: {}", e.getMessage());
                                ttsFailed.set(true);
                                sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                            }
                        });
                    }

                    // Gracefully shutdown queue and await remaining tasks
                    ttsExecutor.shutdown();
                    try {
                        ttsExecutor.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}

                    // 4. Save Assistant reply
                    String finalReply = assistantReply.toString();
                    boolean shouldAdvance = false;
                    if (finalReply.contains(STAGE_COMPLETE_TAG)) {
                        finalReply = finalReply.replace(STAGE_COMPLETE_TAG, "").trim();
                        shouldAdvance = true;
                    }

                    if (!finalReply.isEmpty()) {
                        insertMessage(activeSessionId, ROLE_ASSISTANT, finalReply, nextSeqNum(activeSessionId));
                    }
                    if (shouldAdvance) {
                        internalAdvanceStage(activeSessionId);
                    }

                    // 5. Notify frontend speech has completed
                    sendJson(session, Map.of("type", "status", "status", "speech_end"));

                    // 6. Async judge score evaluation & dynamic window summarization
                    triggerVoiceJudge(interviewSession, userMsg, session);
                    triggerAsyncSummarizeIfNeeded(interviewSession);

                } catch (Exception e) {
                    log.error("Voice WebSocket processing chain crashed: ", e);
                    sendJson(session, Map.of("type", "error", "message", "网络状况不佳，已为您切回文字模式"));
                } finally {
                    UserContext.remove();
                }
            });
        }
    }

    private void sendJson(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to push websocket message: {}", e.getMessage());
        }
    }

    private int nextSeqNum(Long sessionId) {
        Long count = interviewMessageMapper.selectCount(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId));
        return count == null ? 0 : count.intValue();
    }

    private void insertMessage(Long sessionId, String role, String content, int seqNum) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        message.setCreatedAt(java.time.LocalDateTime.now());
        interviewMessageMapper.insert(message);
    }

    private String extractSentenceIfComplete(StringBuilder builder) {
        String text = builder.toString();
        int cutIdx = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '；' || c == '\n' ||
                c == '.' || c == '?' || c == '!' || c == ';') {
                cutIdx = i;
            }
        }
        if (cutIdx != -1) {
            String sentence = text.substring(0, cutIdx + 1);
            builder.delete(0, cutIdx + 1);
            return sentence;
        }
        return null;
    }

    private List<Map<String, String>> buildContextMessages(Long sessionId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getSeqNum));
        List<InterviewMessage> systemMsgs = new ArrayList<>();
        List<InterviewMessage> dialogMsgs = new ArrayList<>();

        for (InterviewMessage m : allMessages) {
            if ("system".equals(m.getRole())) {
                systemMsgs.add(m);
            } else {
                dialogMsgs.add(m);
            }
        }

        String latestUserMsg = "";
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            InterviewMessage m = allMessages.get(i);
            if (ROLE_USER.equals(m.getRole())) {
                latestUserMsg = m.getContent();
                break;
            }
        }
        if (latestUserMsg.isEmpty() && session != null) {
            latestUserMsg = session.getTargetPosition();
        }

        List<String> ragChunks = (session != null && !latestUserMsg.isEmpty())
                ? sessionRagService.searchTopChunks(session.getId(), latestUserMsg, 5)
                : List.of();

        String ragSystemPrompt = "";
        if (!ragChunks.isEmpty()) {
            StringBuilder sb = new StringBuilder("以下是与当前对话主题最相关的简历及岗位 JD 背景信息碎片，供提问和追问参考：\n");
            for (int i = 0; i < ragChunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(ragChunks.get(i)).append("\n");
            }
            ragSystemPrompt = sb.toString();
        }

        String summary = session != null ? session.getSummary() : null;
        if (summary != null && !summary.isBlank()) {
            List<Map<String, String>> messages = new ArrayList<>();
            for (InterviewMessage sysMsg : systemMsgs) {
                messages.add(Map.of("role", "system", "content", sysMsg.getContent()));
            }
            if (!ragSystemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", ragSystemPrompt));
            }
            messages.add(Map.of("role", "system", "content", "以下是此前面试对话的摘要总结（已对涉及手机号、邮箱、身份证等用户隐私数据做严格脱敏处理）：\n" + summary));
            int lastCount = Math.min(dialogMsgs.size(), 8);
            List<InterviewMessage> recentDialogs = dialogMsgs.subList(dialogMsgs.size() - lastCount, dialogMsgs.size());
            for (InterviewMessage m : recentDialogs) {
                messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            return messages;
        }

        List<InterviewMessage> finalMessages = new ArrayList<>();
        if (!systemMsgs.isEmpty()) {
            finalMessages.add(systemMsgs.get(0));
        }
        if (!ragSystemPrompt.isEmpty()) {
            InterviewMessage ragMsg = new InterviewMessage();
            ragMsg.setRole("system");
            ragMsg.setContent(ragSystemPrompt);
            finalMessages.add(ragMsg);
        }
        for (int i = 1; i < systemMsgs.size(); i++) {
            finalMessages.add(systemMsgs.get(i));
        }

        int maxDialogs = 12;
        List<InterviewMessage> trimmedDialogs = dialogMsgs;
        if (dialogMsgs.size() > maxDialogs) {
            trimmedDialogs = new ArrayList<>(dialogMsgs.subList(dialogMsgs.size() - maxDialogs, dialogMsgs.size()));
        }
        finalMessages.addAll(trimmedDialogs);

        return finalMessages.stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                .toList();
    }

    private void internalAdvanceStage(Long sessionId) {
        InterviewStage active = interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
                .eq(InterviewStage::getSessionId, sessionId)
                .isNull(InterviewStage::getEndedAt)
                .last("LIMIT 1"));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (active != null) {
            active.setEndedAt(now);
            interviewStageMapper.updateById(active);
        }

        String current = active != null ? active.getStageName() : "warmup";
        String next = "closing";
        if ("warmup".equals(current)) next = "technical";
        else if ("technical".equals(current)) next = "deep_dive";

        InterviewStage newStage = new InterviewStage();
        newStage.setSessionId(sessionId);
        newStage.setStageName(next);
        newStage.setStartedAt(now);
        interviewStageMapper.insert(newStage);

        Map<String, String> prompts = Map.of(
            "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。",
            "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。",
            "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。"
        );
        insertMessage(sessionId, "system", prompts.getOrDefault(next, "进入下一阶段"), nextSeqNum(sessionId));
    }

    private void triggerVoiceJudge(InterviewSession session, InterviewMessage userMsg, WebSocketSession wsSession) {
        Long userId = session.getUserId();
        String lockKey = "lock:judge:" + userId + ":" + session.getId();
        boolean lockAcquired = false;
        try {
            // Spin wait for lock
            for (int retry = 0; retry < 10; retry++) {
                Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(30));
                if (Boolean.TRUE.equals(acquired)) {
                    lockAcquired = true;
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!lockAcquired) {
                log.warn("Failed to acquire judge lock for voice user {}, skipping voice judge", userId);
                return;
            }

            List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                    .eq(InterviewMessage::getSessionId, session.getId())
                    .orderByAsc(InterviewMessage::getSeqNum));

            String questionContent = "";
            for (int i = allMessages.size() - 1; i >= 0; i--) {
                InterviewMessage m = allMessages.get(i);
                if ("assistant".equals(m.getRole()) && m.getSeqNum() < userMsg.getSeqNum()) {
                    questionContent = m.getContent();
                    break;
                }
            }

            String judgeResultJson;
            if (devFixtureProperties.isEnabled()) {
                int replyIndex = nextSeqNum(session.getId());
                int score = 7 + (replyIndex % 3);
                judgeResultJson = String.format("{\"score\": %d, \"hint\": \"语音表达流畅，内容符合技术规范要求。\"}", score);
            } else {
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

                try {
                    String judgeOutput = llmRouter.chatWithSnapshot(
                        session.getLlmProvider(),
                        session.getLlmModel(),
                        List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                        ),
                        Map.of("response_format", Map.of("type", "json_object"))
                    );

                    String trimmed = stripJsonFence(judgeOutput);
                    Map<String, Object> map = objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
                    int score = ((Number) map.getOrDefault("score", 7)).intValue();
                    int safeScore = Math.max(1, Math.min(10, score));
                    String hint = (String) map.getOrDefault("hint", "回答已记录");
                    judgeResultJson = String.format("{\"score\": %d, \"hint\": \"%s\"}", safeScore, hint.replace("\"", "\\\""));
                } catch (Exception e) {
                    log.warn("Failed to parse voice judge output: {}", e.getMessage());
                    judgeResultJson = "{\"score\": 7, \"hint\": \"回答已记录，继续加油。\"}";
                }
            }

            try {
                Map<String, Object> parsedMap = objectMapper.readValue(judgeResultJson, new TypeReference<Map<String, Object>>() {});
                int score = ((Number) parsedMap.get("score")).intValue();
                String hint = (String) parsedMap.get("hint");
                userMsg.setScore(score);
                userMsg.setHint(hint);
                interviewMessageMapper.updateById(userMsg);

                sendJson(wsSession, Map.of(
                    "type", "judge",
                    "score", score,
                    "hint", hint
                ));
            } catch (Exception e) {
                log.warn("Failed to update and push message score/hint in voice mode", e);
            }
        } finally {
            if (lockAcquired) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    private void triggerAsyncSummarizeIfNeeded(InterviewSession session) {
        List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, session.getId())
                .orderByAsc(InterviewMessage::getSeqNum));
        List<InterviewMessage> dialogMsgs = new ArrayList<>();
        for (InterviewMessage m : allMessages) {
            if (!"system".equals(m.getRole())) {
                dialogMsgs.add(m);
            }
        }
        int rounds = dialogMsgs.size() / 2;
        if (rounds >= 15 && (rounds - 10) % 5 == 0) {
            int summaryRounds = rounds - 7;
            int msgEndIndex = summaryRounds * 2;
            List<InterviewMessage> messagesToSummarize = dialogMsgs.subList(0, msgEndIndex);

            sseTaskExecutor.execute(() -> {
                try {
                    StringBuilder builder = new StringBuilder();
                    for (InterviewMessage m : messagesToSummarize) {
                        builder.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
                    }
                    String existingSummary = session.getSummary();
                    String prompt = "请对以下模拟面试记录进行简明扼要的摘要总结。要求：保留候选人的核心技术栈、项目细节及表现评估，并进行严格的个人隐私数据脱敏（严禁包含手机号、邮箱、身份证等隐私信息）。以第三人称陈述，字数控制在 200 字以内。\n" +
                                     "已有摘要历史：" + (existingSummary != null ? existingSummary : "无") + "\n" +
                                     "新增面试记录：\n" + builder.toString();

                    String newSummary = devFixtureProperties.isEnabled()
                        ? "dev fixture 下自动生成的模拟对话摘要。候选人对后端架构设计进行了基本的回答，表现稳定。"
                        : llmRouter.chatWithSnapshot(
                            session.getLlmProvider(),
                            session.getLlmModel(),
                            List.of(
                                Map.of("role", "system", "content", "你是严谨的面试总结助手。请直接输出摘要，不要附带任何解释。"),
                                Map.of("role", "user", "content", prompt)
                            )
                        );

                    session.setSummary(newSummary);
                    interviewSessionMapper.updateById(session);
                    log.info("Successfully updated sliding window summary via WebSocket for session {}", session.getId());
                } catch (Exception e) {
                    log.error("Failed to generate sliding window summary via WebSocket", e);
                }
            });
        }
    }

    private String stripJsonFence(String text) {
        if (text == null) return null;
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
}
