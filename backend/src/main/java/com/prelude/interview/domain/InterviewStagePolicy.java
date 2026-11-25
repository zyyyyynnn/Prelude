package com.prelude.interview.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@org.springframework.stereotype.Component
public final class InterviewStagePolicy {

    public static final String WARMUP = "warmup";
    public static final List<String> ORDER = List.of(WARMUP, "technical", "deep_dive", "closing");
    private static final Map<String, Integer> REPLY_QUOTAS = Map.of(
        WARMUP, 1,
        "technical", 2,
        "deep_dive", 2,
        "closing", 1
    );

    public int replyQuota(String stageName) {
        String normalized = normalize(stageName);
        return REPLY_QUOTAS.get(normalized);
    }

    public boolean shouldAdvance(String stageName, int assistantReplies) {
        return assistantReplies >= replyQuota(stageName);
    }

    public String normalize(String stageName) {
        if (stageName == null || stageName.isBlank()) {
            throw new StageTransitionException("stageName 不能为空");
        }
        String normalized = stageName.trim();
        if (!ORDER.contains(normalized)) {
            throw new StageTransitionException("无效的面试阶段");
        }
        return normalized;
    }

    public String requireForwardTransition(String currentStage, String requestedStage) {
        String current = normalize(currentStage);
        String next = normalize(requestedStage);
        int currentIndex = ORDER.indexOf(current);
        int nextIndex = ORDER.indexOf(next);
        if (nextIndex < currentIndex) {
            throw new StageTransitionException("面试阶段不可回退");
        }
        if (nextIndex != currentIndex + 1) {
            throw new StageTransitionException("阶段推进顺序不正确");
        }
        return next;
    }

    public Optional<String> nextAfter(String currentStage) {
        int currentIndex = ORDER.indexOf(normalize(currentStage));
        return currentIndex >= ORDER.size() - 1
            ? Optional.empty()
            : Optional.of(ORDER.get(currentIndex + 1));
    }
}
