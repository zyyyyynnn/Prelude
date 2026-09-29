package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ResumeAgentRunMapper extends BaseMapper<ResumeAgentRunEntity> {

    @Select("SELECT * FROM resume_agent_run WHERE turn_id = #{turnId} ORDER BY id LIMIT 1")
    ResumeAgentRunEntity findByTurn(@Param("turnId") Long turnId);

    /**
     * A run closes once. The running guard is what keeps a retried drain from writing a
     * second outcome over the first one.
     */
    @Update("""
        UPDATE resume_agent_run
        SET status = #{status}, failure_reason = #{failureReason}, finished_at = #{now}
        WHERE id = #{runId} AND status = 'running'
        """)
    int finish(
        @Param("runId") Long runId,
        @Param("status") String status,
        @Param("failureReason") String failureReason,
        @Param("now") LocalDateTime now
    );

    @Select("""
        <script>
        SELECT * FROM resume_agent_run
        WHERE turn_id IN
        <foreach item="id" collection="turnIds" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY id
        </script>
        """)
    List<ResumeAgentRunEntity> listByTurns(@Param("turnIds") Collection<Long> turnIds);
}
