package com.prelude.resume.application;

import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeTurn;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Turns a claimed instruction into a finished one.
 *
 * <p>Both callers go through here: the request that submitted a turn, so the candidate is
 * not left waiting for a scheduler tick, and the drain, so a turn orphaned by a restart or a
 * crash still runs. Only one of them can win {@link ResumeWorkspaceRepository#claimTurn},
 * which is what keeps the two from running the same instruction twice.
 *
 * <p>A failure is recorded on the turn and not rethrown: the instruction is genuinely over,
 * its reason is on screen, and re-throwing would turn a handled outcome into a request error
 * the workspace cannot show.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeTurnProcessor {

    private final ResumeWorkspaceRepository workspace;
    private final ResumeAssistantRunner runner;

    /** Returns false when another caller already claimed this turn. */
    public boolean process(ResumeTurn turn) {
        if (!workspace.claimTurn(turn.id(), LocalDateTime.now())) {
            return false;
        }
        ResumeTurn claimed = workspace
            .findTurn(turn.accountId(), turn.id())
            .orElseThrow(() -> new IllegalStateException("轮次在领取后消失: " + turn.id()));
        try {
            runner.run(claimed.accountId(), claimed);
            workspace.finishTurn(claimed.id(), ResumeTurn.Status.DONE, null, LocalDateTime.now());
        } catch (RuntimeException error) {
            log.warn("简历助手运行失败，turn={}", claimed.id(), error);
            workspace.finishTurn(
                claimed.id(), ResumeTurn.Status.FAILED, reasonOf(error), LocalDateTime.now());
        }
        return true;
    }

    private static String reasonOf(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
