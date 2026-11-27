package com.prelude.interview.infrastructure;

import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.integration.InterviewSessionStatus;
import com.prelude.interview.infrastructure.persistence.InterviewSessionEntity;
import com.prelude.interview.infrastructure.persistence.InterviewSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisInterviewSessionRepository implements InterviewSessionRepository {

    private final InterviewSessionMapper sessionMapper;

    @Override
    public InterviewSession selectById(java.io.Serializable sessionId) {
        InterviewSessionEntity entity = sessionMapper.selectById(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public InterviewSession selectByIdForUpdate(java.io.Serializable sessionId) {
        InterviewSessionEntity entity = sessionMapper.selectByIdForUpdate(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public int add(InterviewSession session) {
        InterviewSessionEntity entity = InterviewSessionEntity.of(session);
        int rows = sessionMapper.insert(entity);
        session.setId(entity.getId());
        return rows;
    }

    @Override
    public int update(InterviewSession session) {
        return sessionMapper.updateById(InterviewSessionEntity.of(session));
    }

    @Override
    public int markGeneratingIfOngoing(Long sessionId, Long accountId) {
        InterviewSessionStatus next = InterviewSessionStatus.ONGOING.startGenerating();
        return sessionMapper.transitionStatusOwned(
            sessionId, accountId, InterviewSessionStatus.ONGOING.wire(), next.wire());
    }

    @Override
    public InterviewSessionStatus lockAppendOrder(Long sessionId) {
        String status = sessionMapper.lockAppendOrder(sessionId);
        return status == null ? null : InterviewSessionStatus.fromWire(status);
    }

    @Override
    public int updateSummary(Long sessionId, String summary) {
        return sessionMapper.updateSummaryIfStatus(sessionId, summary, InterviewSessionStatus.ONGOING.wire());
    }

    @Override
    public int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt) {
        return sessionMapper.updatePinnedAt(sessionId, accountId, pinnedAt);
    }

    @Override
    public int completeReport(Long sessionId, String reportJson) {
        InterviewSessionStatus next = InterviewSessionStatus.GENERATING.finishGenerating();
        return sessionMapper.completeReportIfStatus(
            sessionId, reportJson, InterviewSessionStatus.GENERATING.wire(), next.wire());
    }

    @Override
    public int restoreOngoing(Long sessionId) {
        InterviewSessionStatus next = InterviewSessionStatus.GENERATING.restoreOngoing();
        return sessionMapper.transitionStatus(
            sessionId, InterviewSessionStatus.GENERATING.wire(), next.wire());
    }

    @Override
    public int deleteOwned(Long sessionId, Long accountId) {
        return sessionMapper.deleteOwned(sessionId, accountId);
    }

    @Override
    public List<InterviewSession> listByUser(Long accountId) {
        return sessionMapper.listByUser(accountId).stream()
            .map(InterviewSessionEntity::toDomain)
            .toList();
    }
}
