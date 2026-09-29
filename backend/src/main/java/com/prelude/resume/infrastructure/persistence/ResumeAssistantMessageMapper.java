package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeAssistantMessageMapper extends BaseMapper<ResumeAssistantMessageEntity> {

    @Select("""
        SELECT * FROM resume_assistant_message
        WHERE conversation_id = #{conversationId}
        ORDER BY id
        """)
    List<ResumeAssistantMessageEntity> listByConversation(@Param("conversationId") Long conversationId);

    @Select("""
        <script>
        SELECT * FROM resume_assistant_message
        WHERE turn_id IN
        <foreach item="id" collection="turnIds" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY turn_id, seq_num
        </script>
        """)
    List<ResumeAssistantMessageEntity> listByTurns(@Param("turnIds") Collection<Long> turnIds);

    @Select("""
        SELECT COALESCE(MAX(seq_num), -1) FROM resume_assistant_message
        WHERE turn_id = #{turnId}
        """)
    int maxSeq(@Param("turnId") Long turnId);
}
