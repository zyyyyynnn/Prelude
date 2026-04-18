package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.PositionTemplate;
import com.interview.entity.Resume;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.service.InterviewService;
import com.interview.util.LlmUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final long SSE_TIMEOUT_MS = 120000L;

    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final LlmUtil llmUtil;
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
        session.setStatus(STATUS_ONGOING);
        interviewSessionMapper.insert(session);

        InterviewMessage systemMessage = new InterviewMessage();
        systemMessage.setSessionId(session.getId());
        systemMessage.setRole(ROLE_SYSTEM);
        systemMessage.setContent(position.getSystemPrompt());
        systemMessage.setSeqNum(0);
        interviewMessageMapper.insert(systemMessage);

        return new InterviewStartResponse(session.getId(), position.getName());
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
                session.getCreatedAt()
            ))
            .toList();
    }

    @Override
    public SseEmitter chat(Long sessionId, InterviewChatRequest request) {
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> completeWithError(emitter, "连接超时，请重试"));
        emitter.onError(error -> emitter.complete());

        sseTaskExecutor.execute(() -> {
            UserContext.setCurrentUserId(userId);
            StringBuilder assistantReply = new StringBuilder();
            try {
                InterviewSession session = requireOngoingSession(sessionId, userId);
                insertMessage(session.getId(), ROLE_USER, request.getContent(), nextSeqNum(session.getId()));

                List<Map<String, String>> messages = buildContextMessages(session.getId());
                llmUtil.streamChat(messages, delta -> {
                    assistantReply.append(delta);
                    sendDelta(emitter, delta);
                });

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
        List<InterviewMessage> messages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));

        String prompt = buildFinishPrompt(session, messages);
        String report = llmUtil.chat("你是严谨的面试评估助手，请只输出 Markdown。", prompt);

        session.setStatus(STATUS_FINISHED);
        session.setSummaryReport(report);
        interviewSessionMapper.updateById(session);
        return new InterviewFinishResponse(session.getId(), report, STATUS_FINISHED);
    }

    private InterviewSession requireOngoingSession(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw BusinessException.badRequest("面试会话不存在或无权访问");
        }
        if (!STATUS_ONGOING.equals(session.getStatus())) {
            throw BusinessException.badRequest("面试会话已结束");
        }
        return session;
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
        List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));

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

    private String buildFinishPrompt(InterviewSession session, List<InterviewMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据以下模拟面试记录生成 Markdown 评估报告。目标岗位：")
            .append(session.getTargetPosition())
            .append("""

                报告必须包含：
                1. 技术能力匹配度（1-10分 + 说明）
                2. 表达清晰度（1-10分 + 说明）
                3. 核心优势总结
                4. 改进建议（3条）

                面试记录：
                """);
        for (InterviewMessage message : messages) {
            if (!ROLE_SYSTEM.equals(message.getRole())) {
                builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        return builder.toString();
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
}
