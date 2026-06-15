package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.config.RabbitMqConfig;
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
import com.interview.messaging.ReportJobMessage;
import com.interview.service.DevFixtureService;
import com.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import com.interview.config.SseEmitterRegistry;

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
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";
    private static final com.github.benmanes.caffeine.cache.Cache<String, Object> SESSION_LOCKS =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterAccess(java.time.Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();
    private static final Map<String, String> STAGE_PROMPTS = Map.of(
        STAGE_WARMUP, "当前处于破冰阶段，请从候选人的简历经历入手，提出一条简洁的开场问题。注意：如果你认为破冰已充分，准备进入技术问答，请在回复的最末尾严格附上 [STAGE_COMPLETE] 标识。",
        "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节进行追问。注意：如果技术问答已充分，准备进入深挖阶段，请在末尾严格附上 [STAGE_COMPLETE] 标识。",
        "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。注意：如果深挖结束准备收尾，请在末尾严格附上 [STAGE_COMPLETE] 标识。",
        "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。结束后请明确提示候选人可以点击页面上的「生成报告」按钮查看评估结果。"
    );

    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;
    private final LlmRouter llmRouter;
    private final DevFixtureService devFixtureService;
    private final ObjectMapper objectMapper;
    private final InterviewReportParser interviewReportParser;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final com.interview.service.SessionRagService sessionRagService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final RabbitTemplate rabbitTemplate;

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

        InterviewSession existingSession = interviewSessionMapper.selectOne(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .eq(InterviewSession::getResumeId, resume.getId())
            .eq(InterviewSession::getPositionId, position.getId())
            .eq(InterviewSession::getStatus, STATUS_ONGOING)
            .orderByDesc(InterviewSession::getCreatedAt)
            .last("LIMIT 1"));
        if (existingSession != null) {
            ensureInitialStage(existingSession);
            String currentStage = currentStageName(existingSession.getId());
            return new InterviewStartResponse(
                existingSession.getId(),
                existingSession.getTargetPosition(),
                currentStage == null ? STAGE_WARMUP : currentStage
            );
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
        session.setJdText(request.getJdText());
        interviewSessionMapper.insert(session);

        String resumeText = resume.getRawText();
        String jdText = request.getJdText();
        sseTaskExecutor.execute(() -> {
            sessionRagService.indexSession(session.getId(), resumeText, jdText);
        });

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
                    message.getCreatedAt(),
                    message.getScore(),
                    message.getHint()
                ))
                .toList(),
            session.getResumeId(),
            session.getPositionId(),
            session.getJdText()
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
        if (hasPendingAssistantPrompt(sessionId)) {
            throw BusinessException.badRequest("请先回答当前阶段的面试官提问");
        }

        currentStage.setEndedAt(LocalDateTime.now());
        interviewStageMapper.updateById(currentStage);

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(nextStage);
        stage.setStartedAt(LocalDateTime.now());
        stage.setEndedAt(null);
        interviewStageMapper.insert(stage);

        int seqNum = nextSeqNum(sessionId);
        insertMessage(sessionId, ROLE_SYSTEM, STAGE_PROMPTS.get(nextStage), seqNum);
        if (isDevFixtureEnabled()) {
            insertMessage(sessionId, ROLE_ASSISTANT, devFixtureService.resolveScriptedReply(nextStage, 0), seqNum + 1);
        }
        return new InterviewStageUpdateResponse(stage.getStageName(), stage.getStartedAt());
    }

    @Override
    public SseEmitter chat(Long sessionId, InterviewChatRequest request, boolean autoStart) {
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> completeWithError(emitter, "连接超时，请重试"));
        emitter.onError(error -> emitter.complete());
        
        // Register emitter to receive notifications (e.g. fallback)
        sseEmitterRegistry.register(sessionId, emitter);

        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(userId);
            UserContext.setCurrentSessionId(sessionId);
            StringBuilder assistantReply = new StringBuilder();
            InterviewMessage insertedUserMsg = null;
            boolean assistantPersisted = false;
            try {
                InterviewSession session = requireOngoingSession(sessionId, userId);
                String content = normalizeContent(request == null ? null : request.getContent());
                boolean firstRound = !hasConversationRound(sessionId);

                if (autoStart && firstRound && content.isEmpty()) {
                    List<Map<String, String>> messages = buildAutoStartMessages(session);
                    streamAssistantReply(session.getId(), session.getLlmProvider(), session.getLlmModel(), messages, assistantReply, emitter);
                } else {
                    if (content.isEmpty()) {
                        throw BusinessException.badRequest("回答内容不能为空");
                    }
                    Object lock = SESSION_LOCKS.get(sessionId.toString(), k -> new Object());
                    synchronized (lock) {
                        insertedUserMsg = new InterviewMessage();
                        insertedUserMsg.setSessionId(session.getId());
                        insertedUserMsg.setRole(ROLE_USER);
                        insertedUserMsg.setContent(content);
                        insertedUserMsg.setSeqNum(nextSeqNum(session.getId()));
                        interviewMessageMapper.insert(insertedUserMsg);
                    }

                    List<Map<String, String>> messages = buildContextMessages(session.getId());
                    streamAssistantReply(session.getId(), session.getLlmProvider(), session.getLlmModel(), messages, assistantReply, emitter);
                }

                String finalReply = assistantReply.toString();
                boolean shouldAdvance = false;

                if (finalReply.contains(STAGE_COMPLETE_TAG)) {
                    finalReply = finalReply.replace(STAGE_COMPLETE_TAG, "").trim();
                    shouldAdvance = true;
                }

                if (!finalReply.isEmpty()) {
                    insertMessage(session.getId(), ROLE_ASSISTANT, finalReply, nextSeqNum(session.getId()));
                }
                assistantPersisted = true;

                if (shouldAdvance) {
                    internalAdvanceStage(session.getId());
                }

                if (insertedUserMsg != null) {
                    triggerAsyncJudge(session, insertedUserMsg, emitter);
                    triggerAsyncSummarizeIfNeeded(session);
                } else {
                    emitter.complete();
                }
            } catch (Exception exception) {
                if (insertedUserMsg != null && insertedUserMsg.getId() != null && !assistantPersisted) {
                    interviewMessageMapper.deleteById(insertedUserMsg.getId());
                }
                completeWithError(emitter, exception.getMessage() == null ? "连接已断开，请重试" : exception.getMessage());
            } finally {
                UserContext.remove();
            }
        });

        return emitter;
    }

    @Override
    public InterviewFinishResponse finish(Long sessionId) {
        Long userId = currentUserId();
        InterviewSession session = requireOngoingSession(sessionId, userId);

        session.setStatus("generating");
        interviewSessionMapper.updateById(session);

        String jobId = java.util.UUID.randomUUID().toString();
        try {
            ReportJobMessage job = new ReportJobMessage(sessionId, userId, jobId);
            rabbitTemplate.convertAndSend(
                RabbitMqConfig.REPORT_EXCHANGE,
                RabbitMqConfig.REPORT_ROUTING_KEY,
                job
            );
            log.info("Published report generation job to RabbitMQ for session {} with jobId {}", sessionId, jobId);
        } catch (Exception e) {
            log.error("Failed to publish report generation job to RabbitMQ for session {}", sessionId, e);
            try {
                InterviewSession restoreSession = interviewSessionMapper.selectById(sessionId);
                if (restoreSession != null && "generating".equals(restoreSession.getStatus())) {
                    restoreSession.setStatus(STATUS_ONGOING);
                    interviewSessionMapper.updateById(restoreSession);
                    log.info("Restored session {} status to ongoing after publish failure", sessionId);
                }
            } catch (Exception restoreEx) {
                log.error("Failed to restore session {} status to ongoing", sessionId, restoreEx);
            }
            throw BusinessException.badRequest("报告生成任务发布失败");
        }

        SESSION_LOCKS.invalidate(sessionId.toString());

        return new InterviewFinishResponse(session.getId(), null, "generating", jobId);
    }

    @Override
    public SseEmitter listen(Long sessionId) {
        Long userId = currentUserId();
        requireOwnedSession(sessionId, userId);

        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout
        sseEmitterRegistry.register(sessionId, emitter);

        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitter.complete();
        }

        return emitter;
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
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        List<InterviewMessage> allMessages = listMessages(sessionId);
        List<InterviewMessage> systemMsgs = new ArrayList<>();
        List<InterviewMessage> dialogMsgs = new ArrayList<>();

        for (InterviewMessage m : allMessages) {
            if (ROLE_SYSTEM.equals(m.getRole())) {
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
            ragMsg.setRole(ROLE_SYSTEM);
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
        builder.append("请根据以下模拟面试记录生成结构化 JSON 评估结果。目标岗位：")
            .append(session.getTargetPosition())
            .append("""

                reportMarkdown 字段中的 Markdown 报告必须包含以下固定字段：
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

    private void persistScoreHistory(InterviewSession session, InterviewReportParser.ParsedReport report) {
        try {
            ScoreHistory score = buildScoreHistory(session, report);
            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .eq(ScoreHistory::getSessionId, session.getId()));
            scoreHistoryMapper.insert(score);
        } catch (Exception exception) {
            log.warn("Failed to persist score history for session {}", session.getId(), exception);
        }
    }

    private ScoreHistory buildScoreHistory(InterviewSession session, InterviewReportParser.ParsedReport report) {
        ScoreHistory score = new ScoreHistory();
        score.setUserId(session.getUserId());
        score.setSessionId(session.getId());
        score.setTechnicalScore(report.technicalScore());
        score.setExpressionScore(report.expressionScore());
        score.setLogicScore(report.logicScore());
        return score;
    }

    private void persistWeaknesses(InterviewSession session, String report) {
        try {
            List<UserWeakness> weaknesses = isDevFixtureEnabled()
                ? devFixtureService.buildWeaknesses(session.getUserId(), session.getId())
                : extractWeaknesses(session, report);
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

    private void streamAssistantReply(
        Long sessionId,
        String providerKey,
        String model,
        List<Map<String, String>> messages,
        StringBuilder assistantReply,
        SseEmitter emitter
    ) {
        if (isDevFixtureEnabled()) {
            String currentStage = currentStageName(sessionId);
            int replyIndex = assistantRepliesInCurrentStage(sessionId);
            String scriptedReply = devFixtureService.resolveScriptedReply(currentStage, replyIndex);
            devFixtureService.streamReply(scriptedReply, delta -> {
                assistantReply.append(delta);
                sendDelta(emitter, delta);
            });
            return;
        }

        llmRouter.streamWithSnapshot(providerKey, model, messages, delta -> {
            assistantReply.append(delta);
            sendDelta(emitter, delta);
        });
    }

    private int assistantRepliesInCurrentStage(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        List<InterviewMessage> messages = listMessages(sessionId);
        if (stage == null || stage.getStartedAt() == null) {
            return (int) messages.stream()
                .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
                .count();
        }
        String stagePrompt = STAGE_PROMPTS.get(stage.getStageName());
        if (stagePrompt != null && !STAGE_WARMUP.equals(stage.getStageName())) {
            Integer stagePromptSeq = messages.stream()
                .filter(message -> ROLE_SYSTEM.equals(message.getRole()))
                .filter(message -> stagePrompt.equals(message.getContent()))
                .map(InterviewMessage::getSeqNum)
                .filter(seqNum -> seqNum != null)
                .max(Integer::compareTo)
                .orElse(null);
            if (stagePromptSeq != null) {
                return (int) messages.stream()
                    .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
                    .filter(message -> message.getSeqNum() != null && message.getSeqNum() > stagePromptSeq)
                    .count();
            }
        }
        LocalDateTime stageStartedAt = stage.getStartedAt().minusSeconds(1);
        return (int) messages.stream()
            .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
            .filter(message -> message.getCreatedAt() == null || !message.getCreatedAt().isBefore(stageStartedAt))
            .count();
    }

    private boolean hasPendingAssistantPrompt(Long sessionId) {
        List<InterviewMessage> messages = listMessages(sessionId);
        int lastSystemIndex = -1;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (ROLE_SYSTEM.equals(messages.get(index).getRole())) {
                lastSystemIndex = index;
                break;
            }
        }
        int startIndex = lastSystemIndex + 1;
        boolean hasAssistant = false;
        boolean hasUser = false;
        for (int index = startIndex; index < messages.size(); index++) {
            String role = messages.get(index).getRole();
            if (ROLE_ASSISTANT.equals(role)) {
                hasAssistant = true;
            } else if (ROLE_USER.equals(role)) {
                hasUser = true;
            }
        }
        return hasAssistant && !hasUser;
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
        trimmed = trimmed.trim();

        // 兜底：LLM 在 JSON 前输出推理文字时，提取第一个完整数组
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            int start = trimmed.indexOf('[');
            int end   = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
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

    private boolean isDevFixtureEnabled() {
        return devFixtureService != null && devFixtureService.isEnabled();
    }

    private void internalAdvanceStage(Long sessionId) {
        InterviewStage currentStage = currentOrLatestStage(sessionId);
        if (currentStage == null) return;

        int currentIndex = STAGE_ORDER.indexOf(currentStage.getStageName());
        if (currentIndex < 0) {
            log.warn("Unknown stage name '{}' for session {}, forcing to warmup", currentStage.getStageName(), sessionId);
            currentIndex = 0;
        }
        if (currentIndex >= STAGE_ORDER.size() - 1) {
            closeCurrentStage(sessionId);
            return;
        }

        String nextStage = STAGE_ORDER.get(currentIndex + 1);

        currentStage.setEndedAt(LocalDateTime.now());
        interviewStageMapper.updateById(currentStage);

        InterviewStage newStage = new InterviewStage();
        newStage.setSessionId(sessionId);
        newStage.setStageName(nextStage);
        newStage.setStartedAt(LocalDateTime.now());
        interviewStageMapper.insert(newStage);

        insertMessage(sessionId, ROLE_SYSTEM, STAGE_PROMPTS.get(nextStage), nextSeqNum(sessionId));
    }

    private void triggerAsyncJudge(InterviewSession session, InterviewMessage userMsg, SseEmitter emitter) {
        Long userId = session.getUserId();
        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(userId);
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
                    log.warn("Failed to acquire judge lock for user {}, skipping judge", userId);
                    emitter.complete();
                    return;
                }

                // Retrieve last assistant message as the question
                List<InterviewMessage> allMessages = listMessages(session.getId());
                String questionContent = "";
                for (int i = allMessages.size() - 1; i >= 0; i--) {
                    InterviewMessage m = allMessages.get(i);
                    if (ROLE_ASSISTANT.equals(m.getRole()) && m.getSeqNum() < userMsg.getSeqNum()) {
                        questionContent = m.getContent();
                        break;
                    }
                }

                String judgeResultJson;
                if (isDevFixtureEnabled()) {
                    // Scripted dev fixture mock judge
                    String currentStage = currentStageName(session.getId());
                    int replyIndex = assistantRepliesInCurrentStage(session.getId());
                    judgeResultJson = devFixtureService.resolveMockJudge(currentStage, replyIndex);
                    // Stream delay to simulate thinking/processing
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
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
                    try {
                        Map<String, Object> map = objectMapper.readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        int score = ((Number) map.getOrDefault("score", 7)).intValue();
                        int safeScore = Math.max(1, Math.min(10, score));
                        String hint = (String) map.getOrDefault("hint", "回答已记录");
                        judgeResultJson = String.format("{\"score\": %d, \"hint\": \"%s\"}", safeScore, hint.replace("\"", "\\\""));
                    } catch (Exception e) {
                        log.warn("Failed to parse judge output: {}", judgeOutput, e);
                        judgeResultJson = "{\"score\": 7, \"hint\": \"回答已记录，继续加油。\"}";
                    }
                }

                // Save score and hint to database
                try {
                    Map<String, Object> parsedMap = objectMapper.readValue(judgeResultJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    int score = ((Number) parsedMap.get("score")).intValue();
                    String hint = (String) parsedMap.get("hint");
                    userMsg.setScore(score);
                    userMsg.setHint(hint);
                    interviewMessageMapper.updateById(userMsg);
                } catch (Exception e) {
                    log.warn("Failed to update message with score/hint", e);
                }

                // Send event: judge via SSE
                sendJudgeEvent(emitter, judgeResultJson);
                emitter.complete();

            } catch (Exception e) {
                log.error("Error in async judge task", e);
                emitter.complete();
            } finally {
                if (lockAcquired) {
                    stringRedisTemplate.delete(lockKey);
                }
                UserContext.remove();
            }
        });
    }

    private void sendJudgeEvent(SseEmitter emitter, String judgeJson) {
        try {
            emitter.send(SseEmitter.event().name("judge").data(judgeJson));
        } catch (IOException exception) {
            log.warn("Failed to send judge event via SSE", exception);
        }
    }

    private void triggerAsyncSummarizeIfNeeded(InterviewSession session) {
        List<InterviewMessage> allMessages = listMessages(session.getId());
        List<InterviewMessage> dialogMsgs = new ArrayList<>();
        for (InterviewMessage m : allMessages) {
            if (!ROLE_SYSTEM.equals(m.getRole())) {
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

                    String newSummary = isDevFixtureEnabled()
                        ? "dev fixture 下自动生成的模拟对话摘要。候选人对后端架构设计、MyBatis-Plus 分页与自定义 SQL 执行进行了基本的回答，表现稳定。"
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
                    log.info("Successfully updated sliding window memory summary for session {}", session.getId());
                } catch (Exception e) {
                    log.error("Failed to generate sliding window memory summary for session {}", session.getId(), e);
                }
            });
        }
    }

    private record WeaknessExtractionItem(String category, String description) {
    }
}
