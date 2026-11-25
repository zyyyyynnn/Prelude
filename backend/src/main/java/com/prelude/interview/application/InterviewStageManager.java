package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.InterviewStagePolicy;
import com.prelude.interview.domain.StageTransitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InterviewStageManager {

    public static final String STAGE_WARMUP = InterviewStagePolicy.WARMUP;

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final Map<String, String> STAGE_PROMPTS = Map.of(
        STAGE_WARMUP, "当前处于破冰阶段，请从候选人的简历经历入手，提出一条简洁的开场问题。",
        "technical", "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。",
        "deep_dive", "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。",
        "closing", "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。结束后请明确提示候选人可以点击页面上的「生成报告」按钮查看评估结果。"
    );

    private final InterviewStageRepository interviewStageRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewMessageService interviewMessageService;
    private final InterviewStagePolicy stagePolicy;

    @Transactional(rollbackFor = Exception.class)
    public void ensureInitialStage(InterviewSession session) {
        lockOngoing(session.getId());
        if (currentOrLatestStageForUpdate(session.getId()) != null) {
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

    private InterviewStage currentOrLatestStageForUpdate(Long sessionId) {
        InterviewStage current = interviewStageRepository.findCurrentForUpdate(sessionId);
        if (current != null) {
            return current;
        }
        return interviewStageRepository.findLatestForUpdate(sessionId);
    }

    public String currentStageName(Long sessionId) {
        InterviewStage stage = currentOrLatestStage(sessionId);
        return stage == null ? STAGE_WARMUP : stage.getStageName();
    }

    public List<InterviewStage> listStages(Long sessionId) {
        return interviewStageRepository.listBySession(sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewStage moveToStage(Long sessionId, String requestedStage) {
        lockOngoing(sessionId);
        InterviewStage currentStage = requireCurrentStage(sessionId);
        String nextStage = normalizeStageName(requestedStage);
        String currentStageName = currentStage.getStageName();
        if (currentStageName.equals(nextStage)) {
            return currentStage;
        }
        nextStage = requireForwardTransition(currentStageName, nextStage);
        if (hasPendingAssistantPrompt(sessionId)) {
            throw BusinessException.badRequest("请先回答当前阶段的面试官提问");
        }
        return insertNextStage(sessionId, currentStage, nextStage);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewStage advanceIfReady(Long sessionId) {
        lockOngoing(sessionId);
        InterviewStage currentStage = requireCurrentStage(sessionId);
        try {
            if (!stagePolicy.shouldAdvance(currentStage.getStageName(), assistantRepliesInCurrentStage(sessionId))) {
                return currentStage;
            }
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
        Optional<String> nextStage;
        try {
            nextStage = stagePolicy.nextAfter(currentStage.getStageName());
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
        if (nextStage.isEmpty()) {
            closeCurrentStageUnlocked(sessionId);
            return currentStage;
        }
        return insertNextStage(sessionId, currentStage, nextStage.orElseThrow());
    }

    @Transactional(rollbackFor = Exception.class)
    public void closeCurrentStage(Long sessionId) {
        lockOngoing(sessionId);
        closeCurrentStageUnlocked(sessionId);
    }

    private void closeCurrentStageUnlocked(Long sessionId) {
        InterviewStage stage = interviewStageRepository.findCurrentForUpdate(sessionId);
        if (stage != null) {
            stage.setEndedAt(LocalDateTime.now());
            interviewStageRepository.update(stage);
        }
    }

    private void lockOngoing(Long sessionId) {
        String status = interviewSessionRepository.lockAppendOrder(sessionId);
        if (status == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        if (!InterviewSessionStatus.ONGOING.matches(status)) {
            throw BusinessException.badRequest("面试会话已结束或正在生成报告");
        }
    }

    int assistantRepliesInCurrentStage(Long sessionId) {
        InterviewStage stage = currentOrLatestStageForUpdate(sessionId);
        if (stage == null) {
            return 0;
        }
        List<InterviewMessage> messages = listMessages(sessionId);
        int boundarySeq = stageBoundarySeq(messages, stage);
        return (int) messages.stream()
            .filter(message -> ROLE_ASSISTANT.equals(message.getRole()))
            .filter(message -> message.getSeqNum() != null && message.getSeqNum() > boundarySeq)
            .count();
    }

    public String normalizeStageName(String stageName) {
        try {
            return stagePolicy.normalize(stageName);
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    private InterviewStage requireCurrentStage(Long sessionId) {
        InterviewStage stage = currentOrLatestStageForUpdate(sessionId);
        if (stage == null) {
            throw BusinessException.badRequest("面试阶段不存在");
        }
        return stage;
    }

    private String requireForwardTransition(String currentStage, String nextStage) {
        try {
            return stagePolicy.requireForwardTransition(currentStage, nextStage);
        } catch (StageTransitionException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    private InterviewStage insertNextStage(Long sessionId, InterviewStage currentStage, String nextStage) {
        LocalDateTime now = LocalDateTime.now();
        currentStage.setEndedAt(now);
        interviewStageRepository.update(currentStage);

        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(nextStage);
        stage.setStartedAt(now);
        stage.setEndedAt(null);
        interviewStageRepository.add(stage);

        interviewMessageService.insertMessage(sessionId, ROLE_SYSTEM, STAGE_PROMPTS.get(nextStage));
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
        boolean hasAssistant = false;
        boolean hasUser = false;
        for (int index = lastSystemIndex + 1; index < messages.size(); index++) {
            String role = messages.get(index).getRole();
            if (ROLE_ASSISTANT.equals(role)) {
                hasAssistant = true;
            } else if ("user".equals(role)) {
                hasUser = true;
            }
        }
        return hasAssistant && !hasUser;
    }

    private int stageBoundarySeq(List<InterviewMessage> messages, InterviewStage stage) {
        if (stage.getStartedAt() != null) {
            Optional<Integer> sameTimestampPromptSeq = messages.stream()
                .filter(message -> ROLE_SYSTEM.equals(message.getRole()))
                .filter(message -> message.getCreatedAt() != null)
                .filter(message -> message.getCreatedAt().equals(stage.getStartedAt()))
                .map(InterviewMessage::getSeqNum)
                .filter(seqNum -> seqNum != null)
                .max(Integer::compareTo);
            if (sameTimestampPromptSeq.isPresent()) {
                return sameTimestampPromptSeq.get();
            }
            Optional<Integer> stagePromptSeq = messages.stream()
                .filter(message -> ROLE_SYSTEM.equals(message.getRole()))
                .filter(message -> message.getCreatedAt() != null)
                .filter(message -> !message.getCreatedAt().isBefore(stage.getStartedAt()))
                .map(InterviewMessage::getSeqNum)
                .filter(seqNum -> seqNum != null)
                .min(Integer::compareTo);
            if (stagePromptSeq.isPresent()) {
                return stagePromptSeq.get();
            }
            Optional<Integer> firstMessageSeq = messages.stream()
                .filter(message -> message.getCreatedAt() != null)
                .filter(message -> !message.getCreatedAt().isBefore(stage.getStartedAt()))
                .map(InterviewMessage::getSeqNum)
                .filter(seqNum -> seqNum != null)
                .min(Integer::compareTo);
            if (firstMessageSeq.isPresent()) {
                return firstMessageSeq.get() - 1;
            }
        }
        return messages.stream()
            .filter(message -> ROLE_SYSTEM.equals(message.getRole()))
            .map(InterviewMessage::getSeqNum)
            .filter(seqNum -> seqNum != null)
            .max(Integer::compareTo)
            .orElse(-1);
    }

    private List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageRepository.listBySession(sessionId);
    }
}
