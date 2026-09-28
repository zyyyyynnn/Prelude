package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeTurnMapper extends BaseMapper<ResumeTurnEntity> {

    /** Conversations among `ids` that still have a queued or running turn. */
    @Select("""
        <script>
        SELECT DISTINCT conversation_id FROM resume_turn
        WHERE conversation_id IN
        <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
        AND status IN ('queued', 'running')
        </script>
        """)
    List<Long> findActiveConversationIds(@Param("ids") Collection<Long> ids);

    /** Conversations among `ids` that carry at least one turn, active or done. */
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

    @Select("""
        SELECT * FROM resume_turn
        WHERE conversation_id = #{conversationId}
        ORDER BY id
        """)
    List<ResumeTurnEntity> listByConversation(@Param("conversationId") Long conversationId);

    /** The running turn of a conversation, or null. */
    @Select("""
        SELECT * FROM resume_turn
        WHERE conversation_id = #{conversationId} AND status = 'running'
        ORDER BY id LIMIT 1
        """)
    ResumeTurnEntity findRunning(@Param("conversationId") Long conversationId);

    @Select("""
        SELECT * FROM resume_turn
        WHERE conversation_id = #{conversationId} AND status = 'queued'
        ORDER BY queue_position LIMIT 1
        """)
    ResumeTurnEntity findNextQueued(@Param("conversationId") Long conversationId);

    @Select("""
        SELECT COALESCE(MAX(queue_position), 0) FROM resume_turn
        WHERE conversation_id = #{conversationId}
        """)
    int maxQueuePosition(@Param("conversationId") Long conversationId);
}
