package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ResumeTurnMapper extends BaseMapper<ResumeTurnEntity> {

    /** Conversations among {@code ids} that still have a turn awaiting its turn or in flight. */
    @Select("""
        <script>
        SELECT DISTINCT conversation_id FROM resume_turn
        WHERE conversation_id IN
        <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
        AND status IN ('queued', 'running')
        </script>
        """)
    List<Long> findActiveConversationIds(@Param("ids") Collection<Long> ids);

    /** Conversations among {@code ids} that carry at least one turn, in any state. */
    @Select("""
        <script>
        SELECT DISTINCT conversation_id FROM resume_turn
        WHERE conversation_id IN
        <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Long> findConversationIdsWithTurns(@Param("ids") Collection<Long> ids);

    @Select("SELECT * FROM resume_turn WHERE id = #{turnId} AND account_id = #{accountId}")
    ResumeTurnEntity findOwned(@Param("turnId") Long turnId, @Param("accountId") Long accountId);

    @Select("SELECT * FROM resume_turn WHERE id = #{turnId}")
    ResumeTurnEntity findById(@Param("turnId") Long turnId);

    @Select("""
        SELECT * FROM resume_turn
        WHERE conversation_id = #{conversationId}
        ORDER BY queue_position
        """)
    List<ResumeTurnEntity> listByConversation(@Param("conversationId") Long conversationId);

    /**
     * The one claim of a turn: a queued row becomes running for exactly one caller.
     * Both the submitting request and the scheduled drain race here, and the affected
     * row count is the lease — no separate claim token is needed.
     */
    @Update("""
        UPDATE resume_turn SET status = 'running', started_at = #{now}
        WHERE id = #{turnId} AND status = 'queued'
        """)
    int claim(@Param("turnId") Long turnId, @Param("now") LocalDateTime now);

    @Update("""
        UPDATE resume_turn
        SET status = #{status}, completed_at = #{now}, failure_reason = #{failureReason}
        WHERE id = #{turnId} AND status = 'running'
        """)
    int finish(
        @Param("turnId") Long turnId,
        @Param("status") String status,
        @Param("failureReason") String failureReason,
        @Param("now") LocalDateTime now
    );

    /**
     * Withdrawing is its own transition: a turn that has not started goes from queued to
     * cancelled, and one already running is left for {@link #finish}. Reusing the running
     * guard here would make every withdrawal a silent no-op.
     */
    @Update("""
        UPDATE resume_turn SET status = 'cancelled', completed_at = #{now}
        WHERE id = #{turnId} AND status = 'queued'
        """)
    int cancelQueued(@Param("turnId") Long turnId, @Param("now") LocalDateTime now);

    /**
     * Queued turns whose conversation has nothing in flight. The conversation-scoped
     * subquery keeps one conversation to one running turn while different conversations
     * drain in parallel.
     */
    @Select("""
        SELECT * FROM resume_turn queued
        WHERE queued.status = 'queued'
          AND NOT EXISTS (
            SELECT 1 FROM resume_turn running
            WHERE running.conversation_id = queued.conversation_id AND running.status = 'running'
          )
        ORDER BY queued.conversation_id, queued.queue_position
        LIMIT #{limit}
        """)
    List<ResumeTurnEntity> findRunnable(@Param("limit") int limit);

    /** Turns abandoned mid-flight by a crash or a failed run; they hold their conversation. */
    @Select("""
        SELECT * FROM resume_turn
        WHERE status = 'running' AND started_at < #{cutoff}
        ORDER BY id
        LIMIT #{limit}
        """)
    List<ResumeTurnEntity> findStaleRunning(
        @Param("cutoff") LocalDateTime cutoff,
        @Param("limit") int limit
    );

    @Select("SELECT COALESCE(MAX(queue_position), 0) FROM resume_turn WHERE conversation_id = #{conversationId}")
    int maxQueuePosition(@Param("conversationId") Long conversationId);
}
