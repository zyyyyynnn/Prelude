package com.prelude.interview.domain;

public enum InterviewStageDefinition {
    WARMUP("warmup", 1),
    TECHNICAL("technical", 2),
    DEEP_DIVE("deep_dive", 2),
    CLOSING("closing", 1);

    private final String key;
    private final int replyQuota;

    InterviewStageDefinition(String key, int replyQuota) {
        this.key = key;
        this.replyQuota = replyQuota;
    }

    public String key() {
        return key;
    }

    public int replyQuota() {
        return replyQuota;
    }

    public static InterviewStageDefinition fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new StageTransitionException("stageName 不能为空");
        }
        String normalized = key.trim();
        for (InterviewStageDefinition definition : values()) {
            if (definition.key.equals(normalized)) {
                return definition;
            }
        }
        throw new StageTransitionException("无效的面试阶段");
    }
}
