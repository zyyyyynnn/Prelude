package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
