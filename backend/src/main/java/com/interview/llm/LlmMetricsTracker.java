package com.interview.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LlmMetricsTracker {

    private final MeterRegistry registry;

    public LlmMetricsTracker(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLatency(String provider, long elapsedNanos) {
        String safeProvider = provider != null ? provider : "unknown";
        Timer.builder("llm.call.latency")
            .description("LLM call latency distribution")
            .tag("provider", safeProvider)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordTokens(String provider, double count) {
        if (count > 0) {
            String safeProvider = provider != null ? provider : "unknown";
            Counter.builder("llm.tokens.consumed")
                .description("Cumulative token consumption")
                .tag("provider", safeProvider)
                .register(registry)
                .increment(count);
        }
    }

    public void recordFailure(String provider) {
        String safeProvider = provider != null ? provider : "unknown";
        Counter.builder("llm.call.failures")
            .description("LLM call failure count")
            .tag("provider", safeProvider)
            .register(registry)
            .increment();
    }
}
