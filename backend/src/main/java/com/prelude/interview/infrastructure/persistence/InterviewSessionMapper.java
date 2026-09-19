package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionMapper extends BaseMapper<InterviewSession>, InterviewSessionRepository {

    @Override
    default int add(InterviewSession session) {
        return insert(session);
    }

    @Override
    default int update(InterviewSession session) {
        return updateById(session);
    }

    @Override
    @Update("""
        UPDATE interview_session
        SET status = 'generating'
        WHERE id = #{sessionId}
          AND account_id = #{accountId}
          AND status = 'ongoing'
        """)
    int markGeneratingIfOngoing(@Param("sessionId") Long sessionId, @Param("accountId") Long accountId);

    @Override
    @Update("""
        UPDATE interview_session
        SET summary = #{summary}
        WHERE id = #{sessionId}
          AND status = 'ongoing'
        """)
    int updateSummary(@Param("sessionId") Long sessionId, @Param("summary") String summary);

    @Update("""
        UPDATE interview_session
        SET status = 'finished', summary_report = #{reportJson}
        WHERE id = #{sessionId}
          AND status = 'generating'
        """)
    int completeReportIfGenerating(
        @Param("sessionId") Long sessionId,
        @Param("reportJson") String reportJson
    );

    @Update("""
        UPDATE interview_session
        SET status = 'ongoing'
        WHERE id = #{sessionId}
          AND status = 'generating'
        """)
    int restoreOngoingIfGenerating(@Param("sessionId") Long sessionId);

    @Override
    @Update("""
        UPDATE interview_session
        SET pinned_at = #{pinnedAt,jdbcType=TIMESTAMP}
        WHERE id = #{sessionId}
          AND account_id = #{accountId}
        """)
    int updatePinnedAt(
        @Param("sessionId") Long sessionId,
        @Param("accountId") Long accountId,
        @Param("pinnedAt") LocalDateTime pinnedAt
    );

    @Override
    @Delete("DELETE FROM interview_session WHERE id = #{sessionId} AND account_id = #{accountId}")
    int deleteOwned(@Param("sessionId") Long sessionId, @Param("accountId") Long accountId);

    @Override
    default List<InterviewSession> listByUser(Long accountId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getAccountId, accountId)
            .last("ORDER BY pinned_at IS NULL, pinned_at DESC, created_at DESC"));
    }
}
