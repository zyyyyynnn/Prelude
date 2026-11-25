package com.prelude.interview;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "prelude.interview.judge.retry")
public record InterviewJudgeRetryProperties(
    boolean enabled,
    Duration pendingAge,
    int batchSize
) {
    public InterviewJudgeRetryProperties {
        if (pendingAge == null || pendingAge.isNegative()) {
            throw new IllegalArgumentException("prelude.interview.judge.retry.pending-age must not be negative");
        }
        if (batchSize <= 0 || batchSize > 1_000) {
            throw new IllegalArgumentException("prelude.interview.judge.retry.batch-size must be between 1 and 1000");
        }
    }
}
