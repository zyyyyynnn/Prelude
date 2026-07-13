package com.interview.interview.application;

import com.interview.shared.api.BusinessException;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.interview.application.port.InterviewStageRepository;
import com.interview.interview.domain.InterviewStagePolicy;
import com.interview.interview.domain.StageTransitionException;
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

    public static final String STAGE_WARMUP = InterviewStagePolicy.WARMUP;

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_USER = "user";
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

    private final InterviewStageRepository interviewStageRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewMessageService interviewMessageService;
    private final InterviewStagePolicy stagePolicy;

    public void ensureInitialStage(InterviewSession session) {
        if (currentOrLatestStage(session.getId()) != null) {
            return;
        }
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(session.getId());
        stage.setStageName(STAGE_WARMUP);
        stage.setStartedAt(LocalDateTime.now());
        stage.setEndedAt(null);
        interviewStageRepository.add(stage);
    }

    public InterviewStage currentOrLatestStage(Long sessionId) {
        InterviewStage current = interviewStageRepository.findCurrent(sessionId);
        if (current != null) {
            return current;
        }
        return interviewStageRepository.findLatest(sessionId);
    }

    public String currentStageName(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        return stage == null ? STAGE_WARMUP : stage.getStageName();
    }

    public List<InterviewStage> listStages(Long sessionId) {
        return interviewStageRepository.listBySession(sessionId);
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
        nextStage = requireForwardTransition(currentStageName, nextStage);
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
        if (!InterviewStagePolicy.ORDER.contains(currentStage.getStageName())) {
            log.warn("Unknown stage name '{}' for session {}, forcing to warmup", currentStage.getStageName(), sessionId);
        }
        java.util.Optional<String> nextStage = stagePolicy.nextAfter(currentStage.getStageName());
        if (nextStage.isEmpty()) {
            closeCurrentStage(sessionId);
            return;
        }
        insertNextStage(sessionId, currentStage, nextStage.orElseThrow(), completionPrompt);
    }

    public void closeCurrentStage(Long sessionId) {
        InterviewStage stage = interviewStageRepository.findCurrent(sessionId);
        if (stage != null) {
            stage.setEndedAt(LocalDateTime.now());
            interviewStageRepository.update(stage);
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
        try {
            return stagePolicy.normalize(stageName);
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    private String requireForwardTransition(String currentStage, String nextStage) {
        try {
            return stagePolicy.requireForwardTransition(currentStage, nextStage);
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    private InterviewStage insertNextStage(Long sessionId, InterviewStage currentStage, String nextStage, boolean completionPrompt) {
        LocalDateTime now = LocalDateTime.now();
        currentStage.setEndedAt(now);
        interviewStageRepository.update(currentStage);

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(nextStage);
        stage.setStartedAt(now);
        stage.setEndedAt(null);
        interviewStageRepository.add(stage);

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
        return interviewMessageRepository.listBySession(sessionId);
    }

    private void insertSystemMessage(Long sessionId, String content) {
        interviewMessageService.insertMessage(sessionId, ROLE_SYSTEM, content);
    }
}
