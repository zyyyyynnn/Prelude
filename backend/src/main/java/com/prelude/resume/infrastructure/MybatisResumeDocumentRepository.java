package com.prelude.resume.infrastructure;

import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.domain.ResumeRevision;
import com.prelude.resume.infrastructure.persistence.ResumeRevisionEntity;
import com.prelude.resume.infrastructure.persistence.ResumeRevisionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisResumeDocumentRepository implements ResumeDocumentRepository {

    private final ResumeRevisionMapper revisions;
    private final ResumeCodec codec;

    public MybatisResumeDocumentRepository(ResumeRevisionMapper revisions, ResumeCodec codec) {
        this.revisions = revisions;
        this.codec = codec;
    }

    @Override
    public Optional<ResumeRevision> latest(Long resumeId) {
        return Optional.ofNullable(revisions.findLatest(resumeId)).map(this::toDomain);
    }

    @Override
    public Optional<ResumeRevision> find(Long resumeId, int revisionNumber) {
        return Optional.ofNullable(revisions.findByNumber(resumeId, revisionNumber)).map(this::toDomain);
    }

    @Override
    public Optional<ResumeRevision> findByProposal(Long proposalId) {
        return Optional.ofNullable(revisions.findByProposal(proposalId)).map(this::toDomain);
    }

    @Override
    public List<ResumeRevision> list(Long resumeId) {
        return revisions.listByResume(resumeId).stream().map(this::toDomain).toList();
    }

    @Override
    public ResumeRevision append(ResumeRevision revision) {
        ResumeRevisionEntity row = new ResumeRevisionEntity();
        row.setResumeId(revision.resumeId());
        row.setRevisionNumber(revision.revisionNumber());
        row.setDocumentJson(codec.writeDocument(revision.document()));
        row.setSummary(revision.summary());
        row.setOrigin(revision.origin().wire());
        row.setProposalId(revision.proposalId());
        row.setCreatedAt(revision.createdAt() == null ? LocalDateTime.now() : revision.createdAt());
        revisions.insert(row);
        return new ResumeRevision(
            row.getId(), revision.resumeId(), revision.revisionNumber(), revision.document(),
            revision.summary(), revision.origin(), revision.proposalId(), row.getCreatedAt());
    }

    private ResumeRevision toDomain(ResumeRevisionEntity row) {
        return new ResumeRevision(
            row.getId(),
            row.getResumeId(),
            row.getRevisionNumber(),
            codec.readDocument(row.getDocumentJson()),
            row.getSummary(),
            ResumeRevision.Origin.fromWire(row.getOrigin()),
            row.getProposalId(),
            row.getCreatedAt()
        );
    }
}
