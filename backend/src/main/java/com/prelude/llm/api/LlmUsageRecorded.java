package com.prelude.llm.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable usage handoff owned by the llm module. Token counts come only
 * from provider responses; cost stays null until telemetry owns authoritative
 * pricing truth.
 */
public record LlmUsageRecorded(
    Long accountId,
    Long snapshotId,
    String purpose,
    String promptId,
    String provider,
    String model,
    Long inputTokens,
    Long outputTokens,
    Long totalTokens,
    Instant occurredAt,
    BigDecimal estimatedCost
) {
}
