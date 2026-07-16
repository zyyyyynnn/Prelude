package com.interview.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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

    @Bean("resumeBackfillExecutor")
    public Executor resumeBackfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("resume-backfill-");
        executor.initialize();
        return executor;
    }
}
