package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeToolCallMapper extends BaseMapper<ResumeToolCallEntity> {

    @Select("SELECT * FROM resume_tool_call WHERE group_id = #{groupId} ORDER BY sort_order, id")
    List<ResumeToolCallEntity> listByGroup(@Param("groupId") Long groupId);

    @Select("""
        SELECT * FROM resume_tool_call
        WHERE group_id IN (SELECT id FROM resume_tool_group
          WHERE message_id IN (SELECT id FROM resume_assistant_message WHERE conversation_id = #{conversationId}))
        ORDER BY group_id, sort_order, id
        """)
    List<ResumeToolCallEntity> listByConversation(@Param("conversationId") Long conversationId);
}
