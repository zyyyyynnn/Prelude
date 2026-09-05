package com.prelude.llm.api;

import java.util.List;

/**
 * Provider capability descriptor served to consumers (settings, composer).
 * Capabilities come from known Spring AI adapter metadata; the UI never
 * guesses.
 */
public record ModelCapabilityResponse(
    String provider,
    String model,
    boolean reasoning,
    boolean structuredOutput,
    boolean toolCalling,
    boolean streaming,
    boolean vision,
    boolean multilingual,
    boolean longContext,
    boolean embedding,
    boolean nativeRealtimeVoice,
    List<ReasoningLevel> supportedReasoningLevels
) {

    public enum ReasoningLevel {
        AUTO,
        LOW,
        MEDIUM,
        HIGH,
        XHIGH,
        MAX
    }
}
