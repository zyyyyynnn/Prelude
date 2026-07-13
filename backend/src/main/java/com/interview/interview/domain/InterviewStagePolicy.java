package com.interview.interview.domain;

import java.util.List;
import java.util.Optional;

public final class InterviewStagePolicy {

    public static final String WARMUP = "warmup";
    public static final List<String> ORDER = List.of(WARMUP, "technical", "deep_dive", "closing");

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
        int currentIndex = ORDER.indexOf(currentStage);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        return currentIndex >= ORDER.size() - 1
            ? Optional.empty()
            : Optional.of(ORDER.get(currentIndex + 1));
    }
}
