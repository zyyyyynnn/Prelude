package com.interview.resume.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.interview.resume.application.port.ResumeImprovementRepository;
import com.interview.resume.domain.ResumeImprovement;
import com.interview.resume.infrastructure.persistence.ResumeImprovementMapper;
import com.interview.resume.infrastructure.persistence.ResumeImprovementRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisResumeImprovementRepository implements ResumeImprovementRepository {

    private final ResumeImprovementMapper mapper;

    @Override
    public List<ResumeImprovement> listBySession(Long sessionId) {
        List<ResumeImprovementRow> rows = mapper.selectList(new LambdaQueryWrapper<ResumeImprovementRow>()
            .eq(ResumeImprovementRow::getSessionId, sessionId)
            .orderByAsc(ResumeImprovementRow::getOrdinal));
        return rows == null ? List.of() : rows.stream().map(this::toDomain).toList();
    }

    @Override
    public List<ResumeImprovement> listByResume(Long resumeId) {
        List<ResumeImprovementRow> rows = mapper.selectList(new LambdaQueryWrapper<ResumeImprovementRow>()
            .eq(ResumeImprovementRow::getResumeId, resumeId)
            .orderByDesc(ResumeImprovementRow::getCreatedAt));
        return rows == null ? List.of() : rows.stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ResumeImprovement> findById(Long improvementId) {
        return Optional.ofNullable(mapper.selectById(improvementId)).map(this::toDomain);
    }

    @Override
    public ResumeImprovement insert(ResumeImprovement improvement) {
        ResumeImprovementRow row = new ResumeImprovementRow();
        row.setUserId(improvement.userId());
        row.setResumeId(improvement.resumeId());
        row.setSessionId(improvement.sessionId());
        row.setOrdinal(improvement.ordinal());
        row.setTargetPath(improvement.targetPath());
        row.setCurrentText(improvement.currentText());
        row.setProposedText(improvement.proposedText());
        row.setRationale(improvement.rationale());
        row.setEvidence(improvement.evidence());
        row.setBaseDocumentVersion(improvement.baseDocumentVersion());
        row.setStatus(improvement.status());
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public boolean decide(
        Long improvementId,
        String expectedStatus,
        String status,
        Integer appliedDocumentVersion
    ) {
        return mapper.update(null, new LambdaUpdateWrapper<ResumeImprovementRow>()
            .eq(ResumeImprovementRow::getId, improvementId)
            .eq(ResumeImprovementRow::getStatus, expectedStatus)
            .set(ResumeImprovementRow::getStatus, status)
            .set(ResumeImprovementRow::getAppliedDocumentVersion, appliedDocumentVersion)
            .set(ResumeImprovementRow::getDecidedAt, LocalDateTime.now())) == 1;
    }

    private ResumeImprovement toDomain(ResumeImprovementRow row) {
        return new ResumeImprovement(
            row.getId(), row.getUserId(), row.getResumeId(), row.getSessionId(),
            row.getOrdinal() == null ? 0 : row.getOrdinal(), row.getTargetPath(), row.getCurrentText(),
            row.getProposedText(), row.getRationale(), row.getEvidence(),
            row.getBaseDocumentVersion() == null ? 0 : row.getBaseDocumentVersion(), row.getStatus(),
            row.getAppliedDocumentVersion(), row.getCreatedAt(), row.getDecidedAt()
        );
    }
}
