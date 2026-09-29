package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.domain.ResumeRevision;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one accepted proposal: the decision and the revision it produces land together.
 *
 * <p>They are in a single transaction on purpose. Recording the decision first and writing the
 * revision in a second one would leave a proposal marked accepted with nothing to show for it
 * if the process died between the two, and the candidate would have no way to learn that their
 * resume was never actually changed.
 */
@Service
@RequiredArgsConstructor
public class ResumePatchApplier {

    private final ResumeTranscriptRepository transcript;
    private final ResumeDocumentRepository documents;
    private final ResumeWorkspaceRepository workspace;

    @Transactional
    public void apply(ResumePatchProposal proposal, Long accountId) {
        if (!transcript.decide(proposal.id(), ResumePatchProposal.Status.ACCEPTED, null,
            accountId, LocalDateTime.now())) {
            throw BusinessException.conflict("resume_proposal_decided", "这个提案已经处理过了");
        }
        ResumeRevision base = documents.find(proposal.resumeId(), proposal.baseRevision())
            .orElseThrow(() -> BusinessException.notFound("提案依据的版本不存在"));
        int latest = documents.latest(proposal.resumeId())
            .map(ResumeRevision::revisionNumber)
            .orElseThrow(() -> BusinessException.notFound("简历没有可应用的版本"));
        ResumeDocument.Applied applied = base.document().apply(proposal.operations());
        documents.append(new ResumeRevision(
            null, proposal.resumeId(), latest + 1, applied.document(),
            proposal.reason(), ResumeRevision.Origin.AGENT_PATCH, proposal.id(), LocalDateTime.now()));
        workspace.touchConversation(proposal.conversationId(), LocalDateTime.now());
    }
}
