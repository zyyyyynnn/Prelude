package com.prelude.interview.domain;

import java.util.Optional;

public final class InterviewStagePolicy {

    public int replyQuota(String stageName) {
        return InterviewStageDefinition.fromKey(stageName).replyQuota();
    }

    public boolean shouldAdvance(String stageName, int assistantReplies) {
        return assistantReplies >= replyQuota(stageName);
    }

    public String normalize(String stageName) {
        return InterviewStageDefinition.fromKey(stageName).key();
    }

    public String requireForwardTransition(String currentStage, String requestedStage) {
        InterviewStageDefinition current = InterviewStageDefinition.fromKey(currentStage);
        InterviewStageDefinition next = InterviewStageDefinition.fromKey(requestedStage);
        if (next.ordinal() < current.ordinal()) {
            throw new StageTransitionException("面试阶段不可回退");
        }
        if (next.ordinal() != current.ordinal() + 1) {
            throw new StageTransitionException("阶段推进顺序不正确");
        }
        return next.key();
    }

    public Optional<String> nextAfter(String currentStage) {
        InterviewStageDefinition current = InterviewStageDefinition.fromKey(currentStage);
        return current.ordinal() >= InterviewStageDefinition.values().length - 1
            ? Optional.empty()
            : Optional.of(InterviewStageDefinition.values()[current.ordinal() + 1].key());
    }
}
