package com.prelude.resume.application.repository;

import com.prelude.resume.domain.ResumeConversation;
import com.prelude.resume.domain.ResumeTurn;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The workspace's own aggregates: conversations and the turns they queue.
 *
 * <p>Turn state moves through {@link #claim} and {@link #finish} only, both of which are
 * conditional updates. That is what lets the submitting request and the scheduled drain
 * race for the same turn without running it twice, and what keeps a finished turn from
 * being overwritten by a late writer.
 */
public interface ResumeWorkspaceRepository {

    Long createConversation(ResumeConversation conversation);

    Optional<ResumeConversation> findConversation(Long accountId, Long conversationId);

    List<ResumeConversation> listConversations(Long accountId);

    void setPinned(Long accountId, Long conversationId, boolean pinned, LocalDateTime now);

    void deleteConversation(Long accountId, Long conversationId);

    void touchConversation(Long conversationId, LocalDateTime now);

    /** Fills an empty resume slot once; false when the conversation already names a document. */
    boolean attachResume(Long conversationId, Long resumeId);

    Long enqueueTurn(ResumeTurn turn);

    Optional<ResumeTurn> findTurn(Long accountId, Long turnId);

    List<ResumeTurn> listTurns(Long conversationId);

    /** True for exactly one caller: the queued row became running here and nowhere else. */
    boolean claimTurn(Long turnId, LocalDateTime now);

    boolean finishTurn(Long turnId, ResumeTurn.Status status, String failureReason, LocalDateTime now);

    /** Withdraws a turn that has not started; false once another path has taken it. */
    boolean cancelQueuedTurn(Long turnId, LocalDateTime now);

    /** Queued turns whose conversation has nothing in flight, in queue order. */
    List<ResumeTurn> findRunnableTurns(int limit);

    List<ResumeTurn> findStaleRunningTurns(LocalDateTime cutoff, int limit);

    Set<Long> conversationIdsWithPendingWork(Collection<Long> conversationIds);

    Set<Long> conversationIdsWithTurns(Collection<Long> conversationIds);
}
