package com.prelude.resume.application.repository;

import com.prelude.resume.domain.ResumeRevision;
import java.util.List;
import java.util.Optional;

/**
 * The revision chain of a resume.
 *
 * <p>Appending is the only write: a revision is never updated, and the database's unique
 * key on {@code (resume_id, revision_number)} is what makes an applied patch idempotent
 * under a retry — the second attempt cannot land a second copy of the same number.
 */
public interface ResumeDocumentRepository {

    Optional<ResumeRevision> latest(Long resumeId);

    Optional<ResumeRevision> find(Long resumeId, int revisionNumber);

    /** The revision an accepted proposal created, so the workspace can point at the result. */
    Optional<ResumeRevision> findByProposal(Long proposalId);

    List<ResumeRevision> list(Long resumeId);

    ResumeRevision append(ResumeRevision revision);
}
