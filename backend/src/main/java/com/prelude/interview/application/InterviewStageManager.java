package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.domain.InterviewStageDefinition;
import com.prelude.interview.domain.InterviewStagePolicy;
import com.prelude.interview.domain.StageTransitionException;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.integration.InterviewSessionStatus;
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

    private static final Map<String, String> STAGE_PROMPTS = Map.of(
        InterviewStageDefinition.WARMUP.key(), "当前处于破冰阶段，请从候选人的简历经历入手，提出一条简洁的开场问题。",
        InterviewStageDefinition.TECHNICAL.key(), "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。",
        InterviewStageDefinition.DEEP_DIVE.key(), "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。",
        InterviewStageDefinition.CLOSING.key(), "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。结束后请明确提示候选人可以点击页面上的「生成报告」按钮查看评估结果。"
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
        insertStage(session.getId(), InterviewStageDefinition.WARMUP, latestMessageSeq(session.getId()));
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
        if (stage == null) {
            throw BusinessException.badRequest("面试阶段不存在");
        }
        return normalizeStageName(stage.getStageName());
    }

    public String currentStageNameForUpdate(Long sessionId) {
        return normalizeStageName(requireCurrentStage(sessionId).getStageName());
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
        if (currentStage.isAwaitingAnswer()) {
            throw BusinessException.badRequest("请先回答当前阶段的面试官提问");
        }
        return insertNextStage(sessionId, currentStage, nextStage);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewStage advanceIfReady(Long sessionId) {
        lockOngoing(sessionId);
        InterviewStage currentStage = requireCurrentStage(sessionId);
        if (!currentStage.isAwaitingAnswer()) {
            return currentStage;
        }
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
        InterviewStage stage = requireCurrentStage(sessionId);
        stage.setEndedAt(LocalDateTime.now());
        stage.setAwaitingAnswer(false);
        interviewStageRepository.update(stage);
    }

    private void lockOngoing(Long sessionId) {
        InterviewSessionStatus status = interviewSessionRepository.lockAppendOrder(sessionId);
        if (status == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        if (status != InterviewSessionStatus.ONGOING) {
            throw BusinessException.badRequest("面试会话已结束或正在生成报告");
        }
    }

    int assistantRepliesInCurrentStage(Long sessionId) {
        InterviewStage stage = requireCurrentStage(sessionId);
        return (int) listMessages(sessionId).stream()
            .filter(message -> "assistant".equals(message.getRole()))
            .filter(message -> message.getSeqNum() != null && message.getSeqNum() > stage.getBoundarySeq())
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
        normalizeStageName(stage.getStageName());
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
        currentStage.setEndedAt(LocalDateTime.now());
        currentStage.setAwaitingAnswer(false);
        interviewStageRepository.update(currentStage);
        InterviewStage stage = insertStage(
            sessionId, InterviewStageDefinition.fromKey(nextStage), latestMessageSeq(sessionId));
        interviewMessageService.insertSystemMessage(sessionId, STAGE_PROMPTS.get(nextStage));
        return stage;
    }

    private InterviewStage insertStage(Long sessionId, InterviewStageDefinition definition, int boundarySeq) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(definition.key());
        stage.setBoundarySeq(boundarySeq);
        stage.setAwaitingAnswer(false);
        stage.setStartedAt(LocalDateTime.now());
        interviewStageRepository.add(stage);
        return stage;
    }

    private int latestMessageSeq(Long sessionId) {
        InterviewMessage latest = interviewMessageRepository.findLatestForAppend(sessionId);
        return latest == null || latest.getSeqNum() == null ? -1 : latest.getSeqNum();
    }

    private List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageRepository.listBySession(sessionId);
    }
}
