package com.interview.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LlmMetricsTracker {

    private final Timer latencyTimer;
    private final Counter tokenCounter;
    private final Counter failureCounter;

    public LlmMetricsTracker(MeterRegistry registry) {
        this.latencyTimer = Timer.builder("llm.call.latency")
            .description("LLM call latency distribution")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        
        this.tokenCounter = Counter.builder("llm.tokens.consumed")
            .description("Cumulative token consumption")
            .register(registry);
            
        this.failureCounter = Counter.builder("llm.call.failures")
            .description("LLM call failure count")
            .register(registry);
    }

    public void recordLatency(long elapsedNanos) {
        latencyTimer.record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordTokens(double count) {
        if (count > 0) {
            tokenCounter.increment(count);
        }
    }

    public void recordFailure() {
        failureCounter.increment();
    }
}
