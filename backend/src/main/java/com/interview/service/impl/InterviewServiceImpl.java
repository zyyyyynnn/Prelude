package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.config.RabbitMqConfig;
import com.interview.config.SseEmitterRegistry;
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
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.messaging.ReportJobMessage;
import com.interview.service.DevFixtureService;
import com.interview.service.InterviewService;
import com.interview.service.SessionRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final long SSE_TIMEOUT_MS = 120000L;
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";
    private static final com.github.benmanes.caffeine.cache.Cache<String, Object> SESSION_LOCKS =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterAccess(java.time.Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();
    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final LlmRouter llmRouter;
    private final DevFixtureService devFixtureService;
    private final InterviewStageManager interviewStageManager;
    private final InterviewContextService interviewContextService;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final SessionRagService sessionRagService;
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
            interviewStageManager.ensureInitialStage(existingSession);
            String currentStage = interviewStageManager.currentStageName(existingSession.getId());
            return new InterviewStartResponse(
                existingSession.getId(),
                existingSession.getTargetPosition(),
                currentStage == null ? InterviewStageManager.STAGE_WARMUP : currentStage
            );
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume.getId());
        session.setPositionId(position.getId());
        session.setTargetPosition(position.getName());
        LlmSelection selection = llmRouter.resolveCurrentUserSelection(request.getLlmModel());
        session.setLlmProvider(selection.providerKey());
        session.setLlmModel(selection.model());
        session.setStatus(STATUS_ONGOING);
        session.setJdText(request.getJdText());
        interviewSessionMapper.insert(session);

        String resumeText = resume.getRawText();
        String jdText = request.getJdText();
        sseTaskExecutor.execute(() -> sessionRagService.indexSession(session.getId(), resumeText, jdText));

        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), 0);
        interviewStageManager.ensureInitialStage(session);

        return new InterviewStartResponse(session.getId(), position.getName(), InterviewStageManager.STAGE_WARMUP);
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
                interviewStageManager.currentStageName(session.getId()),
                session.getLlmProvider(),
                session.getLlmModel(),
                session.getSummaryReport()
            ))
            .toList();
    }

    @Override
    public InterviewMessagesResponse getSessionMessages(Long sessionId) {
        InterviewSession session = requireOwnedSession(sessionId, currentUserId());
        List<InterviewStage> stages = interviewStageManager.listStages(sessionId);
        List<InterviewMessage> messages = listMessages(sessionId);

        return new InterviewMessagesResponse(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus(),
            stages.isEmpty() ? InterviewStageManager.STAGE_WARMUP : stages.get(stages.size() - 1).getStageName(),
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
        if (interviewStageManager.currentOrLatestStage(sessionId) == null) {
            interviewStageManager.ensureInitialStage(session);
        }
        InterviewStage stage = interviewStageManager.moveToStage(sessionId, request.stageName(), true);
        if (isDevFixtureEnabled()) {
            insertMessage(sessionId, ROLE_ASSISTANT, devFixtureService.resolveScriptedReply(stage.getStageName(), 0), nextSeqNum(sessionId));
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
                    List<Map<String, String>> messages = interviewContextService.buildAutoStartMessages(session);
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

                    List<Map<String, String>> messages = interviewContextService.buildContextMessages(session.getId());
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
                    interviewStageManager.advanceStage(session.getId(), true);
                }

                if (insertedUserMsg != null) {
                    triggerAsyncJudge(session, insertedUserMsg, emitter);
                    interviewSummaryService.triggerAsyncSummarizeIfNeeded(session, false);
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

    private void streamAssistantReply(
        Long sessionId,
        String providerKey,
        String model,
        List<Map<String, String>> messages,
        StringBuilder assistantReply,
        SseEmitter emitter
    ) {
        if (isDevFixtureEnabled()) {
            String currentStage = interviewStageManager.currentStageName(sessionId);
            int replyIndex = interviewStageManager.assistantRepliesInCurrentStage(sessionId);
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

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
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

    private void triggerAsyncJudge(InterviewSession session, InterviewMessage userMsg, SseEmitter emitter) {
        Long userId = session.getUserId();
        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(userId);
            try {
                interviewJudgeService.judgeAndPersist(session, userMsg, false)
                    .ifPresent(result -> sendJudgeEvent(emitter, result.json()));
                emitter.complete();
            } catch (Exception exception) {
                log.error("Error in async judge task", exception);
                emitter.complete();
            } finally {
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

}
