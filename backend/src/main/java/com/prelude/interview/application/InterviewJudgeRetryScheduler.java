package com.prelude.interview.application;

import com.prelude.interview.InterviewJudgeRetryProperties;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "prelude.interview.judge.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
class InterviewJudgeRetryScheduler implements SchedulingConfigurer {

    private final InterviewMessageRepository messageRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewJudgeService judgeService;
    private final InterviewJudgeRetryProperties properties;
    private final long intervalMillis;
    private ThreadPoolTaskScheduler taskScheduler;

    InterviewJudgeRetryScheduler(
        InterviewMessageRepository messageRepository,
        InterviewSessionRepository sessionRepository,
        InterviewJudgeService judgeService,
        InterviewJudgeRetryProperties properties,
        @Value("${prelude.interview.judge.retry.interval-ms:60000}") long intervalMillis
    ) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("prelude.interview.judge.retry.interval-ms must be positive");
        }
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.judgeService = judgeService;
        this.properties = properties;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("interview-judge-retry-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        taskScheduler.initialize();
        PeriodicTrigger trigger = new PeriodicTrigger(intervalMillis, TimeUnit.MILLISECONDS);
        trigger.setFixedRate(false);
        taskScheduler.schedule(this::runRetryPass, trigger);
    }

    void retryPendingAssessments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.pendingAge());
        for (InterviewMessage message : messageRepository.findPendingForJudge(cutoff, properties.batchSize())) {
            try {
                InterviewSession session = sessionRepository.selectById(message.getSessionId());
                if (session != null) {
                    judgeService.judgeAndPersist(session, message);
                }
            } catch (RuntimeException exception) {
                log.warn("Pending judge retry failed for message {}", message.getId(), exception);
            }
        }
    }

    private void runRetryPass() {
        try {
            retryPendingAssessments();
        } catch (RuntimeException exception) {
            log.warn("Pending judge retry pass failed", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }
}
