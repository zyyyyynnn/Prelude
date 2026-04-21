package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewMessageItemResponse;
import com.interview.dto.InterviewMessagesResponse;
import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewStageItemResponse;
import com.interview.dto.InterviewStageUpdateRequest;
import com.interview.dto.InterviewStageUpdateResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.PositionTemplate;
import com.interview.entity.Resume;
import com.interview.entity.ScoreHistory;
import com.interview.entity.UserWeakness;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_WARMUP = "warmup";
    private static final List<String> STAGE_ORDER = List.of(STAGE_WARMUP, "technical", "deep_dive", "closing");
    private static final long SSE_TIMEOUT_MS = 120000L;
    private static final Pattern TECHNICAL_SCORE_PATTERN = Pattern.compile("技术能力\\s*[：:]\\s*(10(?:\\.0+)?|\\d(?:\\.\\d+)?)\\s*/\\s*10");
    private static final Pattern EXPRESSION_SCORE_PATTERN = Pattern.compile("表达清晰度\\s*[：:]\\s*(10(?:\\.0+)?|\\d(?:\\.\\d+)?)\\s*/\\s*10");
    private static final Pattern LOGIC_SCORE_PATTERN = Pattern.compile("逻辑思维\\s*[：:]\\s*(10(?:\\.0+)?|\\d(?:\\.\\d+)?)\\s*/\\s*10");
    private static final Map<String, String> STAGE_PROMPTS = Map.of(
        STAGE_WARMUP, "当前处于破冰阶段，请从候选人的简历经历入手，提出一条简洁的开场问题。",
        "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。",
        "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。",
        "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。"
    );

    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewStartResponse start(InterviewStartRequest request) {
        Long userId = currentUserId();
        Resume resume = resumeMapper.selectById(request.getResumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }

        PositionTemplate position = positionTemplateMapper.selectById(request.getPositionId());
        if (position == null) {
            throw BusinessException.badRequest("岗位模板不存在");
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume.getId());
        session.setPositionId(position.getId());
        session.setTargetPosition(position.getName());
        LlmSelection selection = llmRouter.resolveCurrentUserSelection();
        session.setLlmProvider(selection.providerKey());
        session.setLlmModel(selection.model());
        session.setStatus(STATUS_ONGOING);
        interviewSessionMapper.insert(session);

        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), 0);
        ensureInitialStage(session);

        return new InterviewStartResponse(session.getId(), position.getName(), STAGE_WARMUP);
    }

    @Override
    public List<InterviewSessionItemResponse> listCurrentUserSessions() {
        return interviewSessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, currentUserId())
                .orderByDesc(InterviewSession::getCreatedAt))
            .stream()
            .map(session -> new InterviewSessionItemResponse(
                session.getId(),
                session.getTargetPosition(),
                session.getStatus(),
                session.getCreatedAt(),
                currentStageName(session.getId()),
                session.getLlmProvider(),
                session.getLlmModel(),
                session.getSummaryReport()
            ))
            .toList();
    }

    @Override
    public InterviewMessagesResponse getSessionMessages(Long sessionId) {
        InterviewSession session = requireOwnedSession(sessionId, currentUserId());
        List<InterviewStage> stages = listStages(sessionId);
        List<InterviewMessage> messages = listMessages(sessionId);

        return new InterviewMessagesResponse(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus(),
            stages.isEmpty() ? STAGE_WARMUP : stages.get(stages.size() - 1).getStageName(),
            session.getSummaryReport(),
            stages.stream()
                .map(stage -> new InterviewStageItemResponse(stage.getStageName(), stage.getStartedAt(), stage.getEndedAt()))
                .toList(),
            messages.stream()
                .map(message -> new InterviewMessageItemResponse(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getSeqNum(),
                    message.getCreatedAt()
                ))
                .toList()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewStageUpdateResponse updateStage(Long sessionId, InterviewStageUpdateRequest request) {
        InterviewSession session = requireOngoingSession(sessionId, currentUserId());
        InterviewStage currentStage = currentOrLatestStage(sessionId);
        if (currentStage == null) {
            ensureInitialStage(session);
            currentStage = currentOrLatestStage(sessionId);
        }

        String nextStage = normalizeStageName(request.stageName());
        String currentStageName = currentStage.getStageName();

        if (currentStageName.equals(nextStage)) {
            return new InterviewStageUpdateResponse(currentStage.getStageName(), currentStage.getStartedAt());
        }

        int currentIndex = STAGE_ORDER.indexOf(currentStageName);
        int nextIndex = STAGE_ORDER.indexOf(nextStage);
        if (nextIndex < currentIndex) {
            throw BusinessException.badRequest("面试阶段不可回退");
        }
        if (nextIndex != currentIndex + 1) {
            throw BusinessException.badRequest("阶段推进顺序不正确");
        }

        currentStage.setEndedAt(LocalDateTime.now());
        interviewStageMapper.updateById(currentStage);

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(nextStage);
        stage.setStartedAt(LocalDateTime.now());
        stage.setEndedAt(null);
        interviewStageMapper.insert(stage);

        insertMessage(sessionId, ROLE_SYSTEM, STAGE_PROMPTS.get(nextStage), nextSeqNum(sessionId));
        return new InterviewStageUpdateResponse(stage.getStageName(), stage.getStartedAt());
    }

    @Override
    public SseEmitter chat(Long sessionId, InterviewChatRequest request, boolean autoStart) {
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> completeWithError(emitter, "连接超时，请重试"));
        emitter.onError(error -> emitter.complete());

        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(userId);
            StringBuilder assistantReply = new StringBuilder();
            try {
                InterviewSession session = requireOngoingSession(sessionId, userId);
                String content = normalizeContent(request == null ? null : request.getContent());
                boolean firstRound = !hasConversationRound(sessionId);

                if (autoStart && firstRound && content.isEmpty()) {
                    List<Map<String, String>> messages = buildAutoStartMessages(session);
                    llmRouter.streamWithSnapshot(session.getLlmProvider(), session.getLlmModel(), messages, delta -> {
                        assistantReply.append(delta);
                        sendDelta(emitter, delta);
                    });
                } else {
                    if (content.isEmpty()) {
                        throw BusinessException.badRequest("回答内容不能为空");
                    }
                    insertMessage(session.getId(), ROLE_USER, content, nextSeqNum(session.getId()));
                    List<Map<String, String>> messages = buildContextMessages(session.getId());
                    llmRouter.streamWithSnapshot(session.getLlmProvider(), session.getLlmModel(), messages, delta -> {
                        assistantReply.append(delta);
                        sendDelta(emitter, delta);
                    });
                }

                if (!assistantReply.isEmpty()) {
                    insertMessage(session.getId(), ROLE_ASSISTANT, assistantReply.toString(), nextSeqNum(session.getId()));
                }
                emitter.complete();
            } catch (Exception exception) {
                completeWithError(emitter, exception.getMessage() == null ? "连接已断开，请重试" : exception.getMessage());
            } finally {
                UserContext.remove();
            }
        });

        return emitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewFinishResponse finish(Long sessionId) {
        Long userId = currentUserId();
        InterviewSession session = requireOngoingSession(sessionId, userId);
        List<InterviewMessage> messages = listMessages(sessionId);

        String prompt = buildFinishPrompt(session, messages);
        String report = llmRouter.chatWithSnapshot(
            session.getLlmProvider(),
            session.getLlmModel(),
            List.of(
                Map.of("role", "system", "content", "你是严谨的面试评估助手，请只输出 Markdown。"),
                Map.of("role", "user", "content", prompt)
            )
        );

        session.setStatus(STATUS_FINISHED);
        session.setSummaryReport(report);
        interviewSessionMapper.updateById(session);
        closeCurrentStage(sessionId);

        persistScoreHistory(session, report);
        persistWeaknesses(session, report);

        return new InterviewFinishResponse(session.getId(), report, STATUS_FINISHED);
    }

    private InterviewSession requireOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw BusinessException.badRequest("面试会话不存在或无权访问");
        }
        return session;
    }

    private InterviewSession requireOngoingSession(Long sessionId, Long userId) {
        InterviewSession session = requireOwnedSession(sessionId, userId);
        if (!STATUS_ONGOING.equals(session.getStatus())) {
            throw BusinessException.badRequest("面试会话已结束");
        }
        return session;
    }

    private void ensureInitialStage(InterviewSession session) {
        if (currentOrLatestStage(session.getId()) != null) {
            return;
        }

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(session.getId());
        stage.setStageName(STAGE_WARMUP);
        stage.setStartedAt(LocalDateTime.now());
        stage.setEndedAt(null);
        interviewStageMapper.insert(stage);
    }

    private InterviewStage currentOrLatestStage(Long sessionId) {
        InterviewStage current = interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .isNull(InterviewStage::getEndedAt)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
        if (current != null) {
            return current;
        }
        return interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
    }

    private String currentStageName(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        return stage == null ? STAGE_WARMUP : stage.getStageName();
    }

    private List<InterviewStage> listStages(Long sessionId) {
        return interviewStageMapper.selectList(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByAsc(InterviewStage::getStartedAt)
            .orderByAsc(InterviewStage::getId));
    }

    private List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
    }

    private boolean hasConversationRound(Long sessionId) {
        return interviewMessageMapper.selectCount(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .in(InterviewMessage::getRole, ROLE_USER, ROLE_ASSISTANT)) > 0;
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByDesc(InterviewMessage::getSeqNum)
            .last("LIMIT 1"));
        return latest == null ? 0 : latest.getSeqNum() + 1;
    }

    private void insertMessage(Long sessionId, String role, String content, int seqNum) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        interviewMessageMapper.insert(message);
    }

    private List<Map<String, String>> buildContextMessages(Long sessionId) {
        List<InterviewMessage> allMessages = listMessages(sessionId);
        List<InterviewMessage> selectedMessages;
        if (allMessages.size() > 20) {
            selectedMessages = new ArrayList<>();
            selectedMessages.add(allMessages.get(0));
            selectedMessages.addAll(allMessages.subList(Math.max(allMessages.size() - 18, 1), allMessages.size()));
        } else {
            selectedMessages = allMessages;
        }

        return selectedMessages.stream()
            .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
            .toList();
    }

    private List<Map<String, String>> buildAutoStartMessages(InterviewSession session) {
        Resume resume = resumeMapper.selectById(session.getResumeId());
        List<Map<String, String>> messages = new ArrayList<>(buildContextMessages(session.getId()));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请作为模拟面试官主动发起第一问。")
            .append("目标岗位：").append(session.getTargetPosition()).append("。")
            .append("当前阶段：").append(currentStageName(session.getId())).append("。");
        if (resume != null) {
            userPrompt.append("候选人简历文件名：").append(resume.getFileName()).append("。");
            if (resume.getRawText() != null && !resume.getRawText().isBlank()) {
                userPrompt.append("以下是候选人简历摘要，请基于它发问：\n")
                    .append(limitText(resume.getRawText(), 1800));
            }
        }
        userPrompt.append("\n要求：只输出第一条面试问题，不要附加解释。");
        messages.add(Map.of("role", ROLE_USER, "content", userPrompt.toString()));
        return messages;
    }

    private String buildFinishPrompt(InterviewSession session, List<InterviewMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据以下模拟面试记录生成 Markdown 评估报告。目标岗位：")
            .append(session.getTargetPosition())
            .append("""

                报告必须包含以下固定字段：
                技术能力：X/10
                表达清晰度：X/10
                逻辑思维：X/10

                并继续输出以下内容：
                1. 三维评分解释
                2. 核心优势总结
                3. 改进建议（3条）
                4. 总结结论

                面试记录：
                """);
        for (InterviewMessage message : messages) {
            if (!ROLE_SYSTEM.equals(message.getRole())) {
                builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        return builder.toString();
    }

    private void persistScoreHistory(InterviewSession session, String report) {
        try {
            ScoreHistory score = extractScoreHistory(session, report);
            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .eq(ScoreHistory::getSessionId, session.getId()));
            scoreHistoryMapper.insert(score);
        } catch (Exception exception) {
            log.warn("Failed to persist score history for session {}", session.getId(), exception);
        }
    }

    private ScoreHistory extractScoreHistory(InterviewSession session, String report) {
        Integer technical = extractScore(TECHNICAL_SCORE_PATTERN, report, "技术能力");
        Integer expression = extractScore(EXPRESSION_SCORE_PATTERN, report, "表达清晰度");
        Integer logic = extractScore(LOGIC_SCORE_PATTERN, report, "逻辑思维");

        ScoreHistory score = new ScoreHistory();
        score.setUserId(session.getUserId());
        score.setSessionId(session.getId());
        score.setTechnicalScore(technical);
        score.setExpressionScore(expression);
        score.setLogicScore(logic);
        return score;
    }

    private Integer extractScore(Pattern pattern, String report, String label) {
        Matcher matcher = pattern.matcher(report);
        if (!matcher.find()) {
            throw BusinessException.badRequest(label + "评分提取失败");
        }
        double score = Double.parseDouble(matcher.group(1));
        score = Math.max(0, Math.min(10, score));
        return (int) Math.round(score);
    }

    private void persistWeaknesses(InterviewSession session, String report) {
        try {
            List<UserWeakness> weaknesses = extractWeaknesses(session, report);
            userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
                .eq(UserWeakness::getSessionId, session.getId()));
            for (UserWeakness weakness : weaknesses) {
                userWeaknessMapper.insert(weakness);
            }
        } catch (Exception exception) {
            log.warn("Failed to persist weaknesses for session {}", session.getId(), exception);
        }
    }

    private List<UserWeakness> extractWeaknesses(InterviewSession session, String report) throws JsonProcessingException {
        String content = llmRouter.chatWithSnapshot(
            session.getLlmProvider(),
            session.getLlmModel(),
            List.of(
                Map.of("role", ROLE_SYSTEM, "content", """
                    你是面试分析助手。请只输出严格 JSON 数组，不要输出 Markdown。
                    每个元素必须包含 category 和 description 两个字段。
                    示例：[{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
                    """),
                Map.of("role", ROLE_USER, "content", "请从以下面试报告中提取 1 到 5 个候选人的薄弱点：\n" + report)
            )
        );
        String json = stripJsonFence(content);
        List<WeaknessExtractionItem> items = objectMapper.readValue(json, new TypeReference<>() {
        });
        List<UserWeakness> weaknesses = new ArrayList<>();
        for (WeaknessExtractionItem item : items) {
            if (item.category() == null || item.category().isBlank() || item.description() == null || item.description().isBlank()) {
                continue;
            }
            UserWeakness weakness = new UserWeakness();
            weakness.setUserId(session.getUserId());
            weakness.setSessionId(session.getId());
            weakness.setCategory(item.category().trim());
            weakness.setDescription(item.description().trim());
            weaknesses.add(weakness);
        }
        return weaknesses;
    }

    private void closeCurrentStage(Long sessionId) {
        InterviewStage stage = interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .isNull(InterviewStage::getEndedAt)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
        if (stage != null) {
            stage.setEndedAt(LocalDateTime.now());
            interviewStageMapper.updateById(stage);
        }
    }

    private String normalizeStageName(String stageName) {
        if (stageName == null || stageName.isBlank()) {
            throw BusinessException.badRequest("stageName 不能为空");
        }
        String normalized = stageName.trim();
        if (!STAGE_ORDER.contains(normalized)) {
            throw BusinessException.badRequest("无效的面试阶段");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private String limitText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
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

    private void sendDelta(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().name("message").data(delta));
        } catch (IOException exception) {
            throw BusinessException.badRequest("SSE 推送失败");
        }
    }

    private void completeWithError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException ignored) {
            // Connection may already be closed by browser.
        } finally {
            emitter.complete();
        }
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }

    private record WeaknessExtractionItem(String category, String description) {
    }
}
