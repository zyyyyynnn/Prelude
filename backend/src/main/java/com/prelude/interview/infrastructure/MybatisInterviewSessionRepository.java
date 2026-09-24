package com.prelude.interview.infrastructure;

import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.infrastructure.persistence.InterviewSessionEntity;
import com.prelude.interview.infrastructure.persistence.InterviewSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Adapts the session port to the row table, so the port never carries the row type. */
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
    public String lockAppendOrder(Long sessionId) {
        return sessionMapper.lockAppendOrder(sessionId);
    }

    @Override
    public int markGeneratingIfOngoing(Long sessionId, Long accountId) {
        return sessionMapper.transitionStatusOwned(
            sessionId,
            accountId,
            InterviewSessionStatus.ONGOING.wire(),
            InterviewSessionStatus.GENERATING.wire()
        );
    }

    @Override
    public int updateSummary(Long sessionId, String summary) {
        return sessionMapper.updateSummaryIfStatus(
            sessionId,
            summary,
            InterviewSessionStatus.ONGOING.wire()
        );
    }

    @Override
    public int updatePinnedAt(Long sessionId, Long accountId, LocalDateTime pinnedAt) {
        return sessionMapper.updatePinnedAt(sessionId, accountId, pinnedAt);
    }

    @Override
    public int completeReportIfGenerating(Long sessionId, String reportJson) {
        return sessionMapper.completeReportIfStatus(
            sessionId,
            reportJson,
            InterviewSessionStatus.GENERATING.wire(),
            InterviewSessionStatus.FINISHED.wire()
        );
    }

    @Override
    public int restoreOngoingIfGenerating(Long sessionId) {
        return sessionMapper.transitionStatus(
            sessionId,
            InterviewSessionStatus.GENERATING.wire(),
            InterviewSessionStatus.ONGOING.wire()
        );
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
