package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.resume.api.ResumeProposalResponse;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.BlockOperation;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.domain.ResumeRevision;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one door from a model's patch to the resume document.
 *
 * <p>A proposal that has already been decided is refused before anything else is read, so a
 * double-clicked accept is reported as what it is rather than as a stale patch. Applying the
 * accepted patch is a separate collaborator's transaction, so the decision and the revision it
 * produces either both land or neither does.
 *
 * <p>A proposal whose base revision has been overtaken is marked stale and stays refused. The
 * marking is committed on its own: rolling it back would leave the workspace offering an accept
 * button for a patch that can never be applied. Nothing here replays operations onto a newer
 * document — that is how a patch silently overwrites an edit made in the meantime.
 */
@Service
@RequiredArgsConstructor
public class ResumePatchDecisionService {

    private final ResumeTranscriptRepository transcript;
    private final ResumeDocumentRepository documents;
    private final ResumeWorkspaceRepository workspace;
    private final ResumePatchApplier applier;

    public ResumeProposalResponse accept(Long accountId, Long proposalId) {
        ResumePatchProposal proposal = requirePending(accountId, proposalId);
        int latest = latestRevision(proposal.resumeId());
        if (proposal.baseRevision() != latest) {
            transcript.decide(proposalId, ResumePatchProposal.Status.STALE,
                "基线版本已前进到 r" + latest + "，本提案未应用。", accountId, LocalDateTime.now());
            throw BusinessException.conflict("resume_proposal_stale", "提案已失效：简历在这之后又被改过，请重新提出修改");
        }
        applier.apply(proposal, accountId);
        ResumePatchProposal decided = transcript.findProposal(proposalId).orElseThrow();
        markStaleOthers(decided.resumeId(), latest + 1, proposalId);
        return view(decided);
    }

    @Transactional
    public ResumeProposalResponse reject(Long accountId, Long proposalId) {
        ResumePatchProposal proposal = requirePending(accountId, proposalId);
        if (!transcript.decide(proposalId, ResumePatchProposal.Status.REJECTED,
            "候选人拒绝了这个提案。", accountId, LocalDateTime.now())) {
            throw BusinessException.conflict("resume_proposal_decided", "这个提案已经处理过了");
        }
        workspace.touchConversation(proposal.conversationId(), LocalDateTime.now());
        return view(transcript.findProposal(proposalId).orElseThrow());
    }

    /**
     * One proposal landing on a new revision makes every other pending one for that resume
     * stale, because each of them was written against the document that just changed.
     */
    private void markStaleOthers(Long resumeId, int revision, Long exceptProposalId) {
        for (ResumePatchProposal pending : transcript.listPendingProposalsBehindRevision(resumeId, revision)) {
            if (!pending.id().equals(exceptProposalId)) {
                transcript.decide(pending.id(), ResumePatchProposal.Status.STALE,
                    "基线版本已前进到 r" + revision + "，本提案未应用。", null, LocalDateTime.now());
            }
        }
    }

    public ResumeProposalResponse view(ResumePatchProposal proposal) {
        int latest = latestRevision(proposal.resumeId());
        ResumeDocument base = documents
            .find(proposal.resumeId(), proposal.baseRevision())
            .map(ResumeRevision::document)
            .orElse(new ResumeDocument(List.of()));
        Long resultingRevisionId = documents.findByProposal(proposal.id())
            .map(ResumeRevision::id)
            .orElse(null);
        return new ResumeProposalResponse(
            proposal.id(),
            proposal.runId(),
            proposal.resumeId(),
            proposal.baseRevision(),
            latest,
            proposal.isPending() && proposal.baseRevision() == latest,
            proposal.affectedBlockIds(),
            proposal.reason(),
            proposal.operations().stream().map(operation -> change(base, operation)).toList(),
            proposal.factRisk().stream()
                .map(risk -> new ResumeProposalResponse.FactRisk(risk.blockId(), risk.statement()))
                .toList(),
            proposal.status().wire(),
            proposal.decisionNote(),
            proposal.createdAt(),
            proposal.decidedAt(),
            resultingRevisionId
        );
    }

    private static ResumeProposalResponse.Change change(
        ResumeDocument base, BlockOperation operation
    ) {
        return new ResumeProposalResponse.Change(
            operation.kind().name().toLowerCase(java.util.Locale.ROOT),
            operation.blockId(),
            operation.section(),
            operation.blockId() == null
                ? null
                : base.block(operation.blockId()).map(ResumeDocument.Block::text).orElse(null),
            operation.text());
    }

    private ResumePatchProposal requirePending(Long accountId, Long proposalId) {
        ResumePatchProposal proposal = transcript.findProposal(proposalId)
            .orElseThrow(() -> BusinessException.notFound("提案不存在"));
        workspace.findConversation(accountId, proposal.conversationId())
            .orElseThrow(() -> BusinessException.notFound("会话不存在"));
        if (!proposal.isPending()) {
            throw BusinessException.conflict("resume_proposal_decided", "这个提案已经处理过了");
        }
        return proposal;
    }

    private int latestRevision(Long resumeId) {
        return documents.latest(resumeId).map(ResumeRevision::revisionNumber).orElse(0);
    }
}
