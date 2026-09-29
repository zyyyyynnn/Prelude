package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeAgentStepMapper extends BaseMapper<ResumeAgentStepEntity> {

    @Select("""
        SELECT * FROM resume_agent_step
        WHERE run_id = #{runId}
        ORDER BY sort_order, id
        """)
    List<ResumeAgentStepEntity> listByRun(@Param("runId") Long runId);

    @Select("""
        <script>
        SELECT * FROM resume_agent_step
        WHERE run_id IN
        <foreach item="id" collection="runIds" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY run_id, sort_order, id
        </script>
        """)
    List<ResumeAgentStepEntity> listByRuns(@Param("runIds") Collection<Long> runIds);

    @Select("SELECT COALESCE(MAX(sort_order), -1) FROM resume_agent_step WHERE run_id = #{runId}")
    int maxSortOrder(@Param("runId") Long runId);
}
