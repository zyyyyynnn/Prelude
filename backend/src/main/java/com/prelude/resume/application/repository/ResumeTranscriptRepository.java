package com.prelude.resume.application.repository;

import com.prelude.resume.domain.ResumeAgentRun;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumePatchProposal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The record of what an assistant run did and what it asked for: messages, runs, steps and
 * proposals.
 *
 * <p>Decisions are conditional too — {@link #decide} only moves a proposal that is still
 * pending, which is what makes a double-clicked accept create one revision instead of two.
 */
public interface ResumeTranscriptRepository {

    Long appendMessage(Long turnId, Long conversationId, String content, LocalDateTime now);

    List<AssistantMessage> listMessages(Collection<Long> turnIds);

    Long openRun(ResumeAgentRun run);

    Optional<ResumeAgentRun> findRun(Long runId);

    Optional<ResumeAgentRun> findRunByTurn(Long turnId);

    /** The runs behind a page of turns, fetched in one go so a list stays one query. */
    List<ResumeAgentRun> listRuns(Collection<Long> turnIds);

    void finishRun(Long runId, ResumeAgentRun.Status status, String failureReason, LocalDateTime now);

    void appendStep(ResumeAgentStep step);

    List<ResumeAgentStep> listSteps(Collection<Long> runIds);

    int nextStepOrder(Long runId);

    Long recordProposal(ResumePatchProposal proposal);

    Optional<ResumePatchProposal> findProposal(Long proposalId);

    List<ResumePatchProposal> listProposals(Collection<Long> conversationIds);

    /** Applies a decision to a still-pending proposal; false means someone got there first. */
    boolean decide(
        Long proposalId,
        ResumePatchProposal.Status status,
        String decisionNote,
        Long decidedByAccountId,
        LocalDateTime now
    );

    /** Proposals that assumed an older document and must now be reported as stale. */
    List<ResumePatchProposal> listPendingProposalsBehindRevision(Long resumeId, int revision);

    /**
     * An assistant message the workspace renders. Its run is found by turn — a run already
     * carries the turn that triggered it, so the message needs no second link.
     */
    record AssistantMessage(
        Long id,
        Long turnId,
        String content,
        LocalDateTime createdAt
    ) {
    }
}
