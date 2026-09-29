package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ResumeConversationMapper extends BaseMapper<ResumeConversationEntity> {

    @Select("""
        SELECT * FROM resume_conversation
        WHERE id = #{conversationId} AND account_id = #{accountId}
        """)
    ResumeConversationEntity findOwned(
        @Param("conversationId") Long conversationId,
        @Param("accountId") Long accountId
    );

    @Select("""
        SELECT * FROM resume_conversation
        WHERE account_id = #{accountId}
        ORDER BY pinned_at IS NULL, pinned_at DESC, updated_at DESC
        """)
    List<ResumeConversationEntity> listByOwner(@Param("accountId") Long accountId);

    /**
     * Pin state is written with its own statement because clearing it has to reach the
     * column: MyBatis-Plus leaves a null field out of the UPDATE it builds for
     * {@code updateById}, so unpinning through that path would report success and change
     * nothing. {@code interview_session} pins the same way for the same reason.
     */
    @Update("""
        UPDATE resume_conversation
        SET pinned_at = #{pinnedAt,jdbcType=TIMESTAMP}, updated_at = #{now}
        WHERE id = #{conversationId} AND account_id = #{accountId}
        """)
    int updatePinnedAt(
        @Param("conversationId") Long conversationId,
        @Param("accountId") Long accountId,
        @Param("pinnedAt") LocalDateTime pinnedAt,
        @Param("now") LocalDateTime now
    );

    @Update("""
        UPDATE resume_conversation SET updated_at = #{now}
        WHERE id = #{conversationId}
        """)
    int touch(@Param("conversationId") Long conversationId, @Param("now") LocalDateTime now);

    /** Attaching is one-way and only ever fills an empty slot, so a resolved document sticks. */
    @Update("""
        UPDATE resume_conversation SET resume_id = #{resumeId}
        WHERE id = #{conversationId} AND resume_id IS NULL
        """)
    int attachResume(@Param("conversationId") Long conversationId, @Param("resumeId") Long resumeId);

    /** Ownership is part of the statement, so a delete cannot reach another account's row. */
    @Delete("DELETE FROM resume_conversation WHERE id = #{conversationId} AND account_id = #{accountId}")
    int deleteOwned(
        @Param("conversationId") Long conversationId,
        @Param("accountId") Long accountId
    );
}
