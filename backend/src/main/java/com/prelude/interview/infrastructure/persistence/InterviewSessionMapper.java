package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.interview.infrastructure.persistence.InterviewSessionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Session status never appears as a literal here: callers pass the wire values from
 * {@code InterviewSessionStatus}, so the enum is the only place that spells them.
 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {

    @Update("""
        UPDATE interview_session
        SET status = #{toStatus}
        WHERE id = #{sessionId}
          AND account_id = #{accountId}
          AND status = #{fromStatus}
        """)
    int transitionStatusOwned(
        @Param("sessionId") Long sessionId,
        @Param("accountId") Long accountId,
        @Param("fromStatus") String fromStatus,
        @Param("toStatus") String toStatus
    );

    /**
     * Takes the session row's write lock, which is what serialises message appends for one
     * session across every process. Returns the status it locked, or null when there is no session.
     */
    @Select("SELECT status FROM interview_session WHERE id = #{sessionId} FOR UPDATE")
    String lockAppendOrder(@Param("sessionId") Long sessionId);

    @Update("""
        UPDATE interview_session
        SET summary = #{summary}
        WHERE id = #{sessionId}
          AND status = #{fromStatus}
        """)
    int updateSummaryIfStatus(
        @Param("sessionId") Long sessionId,
        @Param("summary") String summary,
        @Param("fromStatus") String fromStatus
    );

    @Update("""
        UPDATE interview_session
        SET status = #{toStatus}, summary_report = #{reportJson}
        WHERE id = #{sessionId}
          AND status = #{fromStatus}
        """)
    int completeReportIfStatus(
        @Param("sessionId") Long sessionId,
        @Param("reportJson") String reportJson,
        @Param("fromStatus") String fromStatus,
        @Param("toStatus") String toStatus
    );

    @Update("""
        UPDATE interview_session
        SET status = #{toStatus}
        WHERE id = #{sessionId}
          AND status = #{fromStatus}
        """)
    int transitionStatus(
        @Param("sessionId") Long sessionId,
        @Param("fromStatus") String fromStatus,
        @Param("toStatus") String toStatus
    );

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

    @Delete("DELETE FROM interview_session WHERE id = #{sessionId} AND account_id = #{accountId}")
    int deleteOwned(@Param("sessionId") Long sessionId, @Param("accountId") Long accountId);

    default List<InterviewSessionEntity> listByUser(Long accountId) {
        return selectList(new LambdaQueryWrapper<InterviewSessionEntity>()
            .eq(InterviewSessionEntity::getAccountId, accountId)
            .last("ORDER BY pinned_at IS NULL, pinned_at DESC, created_at DESC"));
    }
}
