package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeToolGroupMapper extends BaseMapper<ResumeToolGroupEntity> {

    @Select("SELECT * FROM resume_tool_group WHERE message_id = #{messageId}")
    ResumeToolGroupEntity findByMessage(@Param("messageId") Long messageId);

    @Select("""
        SELECT * FROM resume_tool_group
        WHERE message_id IN (SELECT id FROM resume_assistant_message WHERE conversation_id = #{conversationId})
        """)
    List<ResumeToolGroupEntity> listByConversation(@Param("conversationId") Long conversationId);
}
