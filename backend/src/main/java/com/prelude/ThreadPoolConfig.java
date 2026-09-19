package com.prelude;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ThreadPoolConfig {

    @Bean("sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-pool-");
        executor.initialize();
        return executor;
    }

    @Bean("ttsTaskExecutor")
    public SessionKeyedSerialExecutor ttsTaskExecutor(
        @Value("${prelude.voice.tts-pool-size:4}") int poolSize
    ) {
        int normalizedPoolSize = Math.max(1, poolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedPoolSize);
        executor.setMaxPoolSize(normalizedPoolSize);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("tts-");
        executor.initialize();
        return new SessionKeyedSerialExecutor(executor);
    }

    /**
     * Two "stop" frames from the same interview must not run their turns concurrently: they would
     * interleave message sequence numbers, stage advance and judging. Different interviews stay parallel.
     */
    @Bean("voiceTurnExecutor")
    public SessionKeyedSerialExecutor voiceTurnExecutor(
        @Value("${prelude.voice.turn-pool-size:4}") int poolSize
    ) {
        int normalizedPoolSize = Math.max(1, poolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedPoolSize);
        executor.setMaxPoolSize(normalizedPoolSize);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("voice-turn-");
        executor.initialize();
        return new SessionKeyedSerialExecutor(executor);
    }

    /**
     * Keeps an open interview stream alive while a turn is in flight. Without it the emitter
     * hits its timeout during a long model call and the client silently loses the answer.
     */
    @Bean("sseHeartbeatExecutor")
    public ScheduledExecutorService sseHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
