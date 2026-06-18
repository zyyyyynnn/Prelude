package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.BusinessException;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewStageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewStageManager {

    public static final String STAGE_WARMUP = "warmup";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_USER = "user";
    private static final List<String> STAGE_ORDER = List.of(STAGE_WARMUP, "technical", "deep_dive", "closing");
    private static final Map<String, String> COMPLETION_STAGE_PROMPTS = Map.of(
        STAGE_WARMUP, "当前处于破冰阶段，请从候选人的简历经历入手，提出一条简洁的开场问题。注意：如果你认为破冰已充分，准备进入技术问答，请在回复的最末尾严格附上 [STAGE_COMPLETE] 标识。",
        "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节进行追问。注意：如果技术问答已充分，准备进入深挖阶段，请在末尾严格附上 [STAGE_COMPLETE] 标识。",
        "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。注意：如果深挖结束准备收尾，请在末尾严格附上 [STAGE_COMPLETE] 标识。",
        "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。结束后请明确提示候选人可以点击页面上的「生成报告」按钮查看评估结果。"
    );
    private static final Map<String, String> DIRECT_STAGE_PROMPTS = Map.of(
        "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。",
        "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。",
        "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。"
    );

    private final InterviewStageMapper interviewStageMapper;
    private final InterviewMessageMapper interviewMessageMapper;

    public void ensureInitialStage(InterviewSession session) {
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

    public InterviewStage currentOrLatestStage(Long sessionId) {
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

    public String currentStageName(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        return stage == null ? STAGE_WARMUP : stage.getStageName();
    }

    public List<InterviewStage> listStages(Long sessionId) {
        return interviewStageMapper.selectList(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByAsc(InterviewStage::getStartedAt)
            .orderByAsc(InterviewStage::getId));
    }

    public InterviewStage moveToStage(Long sessionId, String requestedStage, boolean completionPrompt) {
        InterviewStage currentStage = currentOrLatestStage(sessionId);
        if (currentStage == null) {
            throw BusinessException.badRequest("面试阶段不存在");
        }
        String nextStage = normalizeStageName(requestedStage);
        String currentStageName = currentStage.getStageName();
        if (currentStageName.equals(nextStage)) {
            return currentStage;
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
        return insertNextStage(sessionId, currentStage, nextStage, completionPrompt);
    }

    public void advanceStage(Long sessionId, boolean completionPrompt) {
        InterviewStage currentStage = currentOrLatestStage(sessionId);
        if (currentStage == null) {
            return;
        }
        int currentIndex = STAGE_ORDER.indexOf(currentStage.getStageName());
        if (currentIndex < 0) {
            log.warn("Unknown stage name '{}' for session {}, forcing to warmup", currentStage.getStageName(), sessionId);
            currentIndex = 0;
        }
        if (currentIndex >= STAGE_ORDER.size() - 1) {
            closeCurrentStage(sessionId);
            return;
        }
        insertNextStage(sessionId, currentStage, STAGE_ORDER.get(currentIndex + 1), completionPrompt);
    }

    public void closeCurrentStage(Long sessionId) {
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

    public int assistantRepliesInCurrentStage(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        List<InterviewMessage> messages = listMessages(sessionId);
        if (stage == null || stage.getStartedAt() == null) {
            return (int) messages.stream()
                .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
                .count();
        }
        Integer stagePromptSeq = latestStagePromptSeq(messages, stage.getStageName());
        if (stagePromptSeq != null) {
            return (int) messages.stream()
                .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
                .filter(message -> message.getSeqNum() != null && message.getSeqNum() > stagePromptSeq)
                .count();
        }
        LocalDateTime stageStartedAt = stage.getStartedAt().minusSeconds(1);
        return (int) messages.stream()
            .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
            .filter(message -> message.getCreatedAt() == null || !message.getCreatedAt().isBefore(stageStartedAt))
            .count();
    }

    public String normalizeStageName(String stageName) {
        if (stageName == null || stageName.isBlank()) {
            throw BusinessException.badRequest("stageName 不能为空");
        }
        String normalized = stageName.trim();
        if (!STAGE_ORDER.contains(normalized)) {
            throw BusinessException.badRequest("无效的面试阶段");
        }
        return normalized;
    }

    private InterviewStage insertNextStage(Long sessionId, InterviewStage currentStage, String nextStage, boolean completionPrompt) {
        LocalDateTime now = LocalDateTime.now();
        currentStage.setEndedAt(now);
        interviewStageMapper.updateById(currentStage);

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(nextStage);
        stage.setStartedAt(now);
        stage.setEndedAt(null);
        interviewStageMapper.insert(stage);

        insertSystemMessage(sessionId, stagePrompt(nextStage, completionPrompt));
        return stage;
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

    private String stagePrompt(String stageName, boolean completionPrompt) {
        if (completionPrompt) {
            return COMPLETION_STAGE_PROMPTS.get(stageName);
        }
        return DIRECT_STAGE_PROMPTS.getOrDefault(stageName, COMPLETION_STAGE_PROMPTS.get(stageName));
    }

    private Integer latestStagePromptSeq(List<InterviewMessage> messages, String stageName) {
        String completionPrompt = COMPLETION_STAGE_PROMPTS.get(stageName);
        String directPrompt = DIRECT_STAGE_PROMPTS.get(stageName);
        if (completionPrompt == null && directPrompt == null) {
            return null;
        }
        return messages.stream()
            .filter(message -> ROLE_SYSTEM.equals(message.getRole()))
            .filter(message -> completionPrompt != null && completionPrompt.equals(message.getContent())
                || directPrompt != null && directPrompt.equals(message.getContent()))
            .map(InterviewMessage::getSeqNum)
            .filter(seqNum -> seqNum != null)
            .max(Integer::compareTo)
            .orElse(null);
    }

    private List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByDesc(InterviewMessage::getSeqNum)
            .last("LIMIT 1"));
        return latest == null ? 0 : latest.getSeqNum() + 1;
    }

    private void insertSystemMessage(Long sessionId, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(ROLE_SYSTEM);
        message.setContent(content);
        message.setSeqNum(nextSeqNum(sessionId));
        interviewMessageMapper.insert(message);
    }
}
