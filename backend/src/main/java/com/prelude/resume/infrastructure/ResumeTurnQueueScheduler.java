package com.prelude.resume.infrastructure;

import com.prelude.resume.application.ResumeTurnProcessor;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeTurn;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the resume queue moving without anyone asking.
 *
 * <p>A turn is normally run by the request that submitted it. This pass exists for the turns
 * that request never got to finish — a restart mid-run, a pod that died holding a claim —
 * which would otherwise sit in the queue while its conversation shows "处理中" forever.
 *
 * <p>Both halves are safe to run twice: the drain only executes turns it wins the claim for,
 * and the sweep closes running turns older than its own cutoff with a conditional update, so
 * a turn that is genuinely still in flight is left alone until it passes that mark.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "prelude.jobs", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ResumeTurnQueueScheduler {

    private static final int DRAIN_BATCH = 8;

    /** A turn still marked running past this age has no live owner; its run stopped reporting. */
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(10);

    private final ResumeWorkspaceRepository workspace;
    private final ResumeTurnProcessor processor;

    @Scheduled(fixedDelayString = "${prelude.resume.queue-drain-delay-ms:5000}")
    public void drain() {
        LocalDateTime now = LocalDateTime.now();
        sweepAbandoned(now);
        for (ResumeTurn turn : workspace.findRunnableTurns(DRAIN_BATCH)) {
            try {
                processor.process(turn);
            } catch (RuntimeException error) {
                log.warn("简历队列排空失败，turn={}", turn.id(), error);
            }
        }
    }

    private void sweepAbandoned(LocalDateTime now) {
        for (ResumeTurn turn : workspace.findStaleRunningTurns(now.minus(ABANDONED_AFTER), DRAIN_BATCH)) {
            boolean closed = workspace.finishTurn(
                turn.id(), ResumeTurn.Status.FAILED, "运行中断，未收到结果", now);
            if (closed) {
                log.warn("简历轮次失去执行者，已标记失败，turn={}", turn.id());
            }
        }
    }
}
