package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeRevisionMapper extends BaseMapper<ResumeRevisionEntity> {

    @Select("""
        SELECT * FROM resume_revision
        WHERE resume_id = #{resumeId}
        ORDER BY revision_number DESC LIMIT 1
        """)
    ResumeRevisionEntity findLatest(@Param("resumeId") Long resumeId);

    @Select("SELECT * FROM resume_revision WHERE resume_id = #{resumeId} AND revision_number = #{revision}")
    ResumeRevisionEntity findByNumber(
        @Param("resumeId") Long resumeId,
        @Param("revision") int revision
    );

    @Select("SELECT * FROM resume_revision WHERE proposal_id = #{proposalId} ORDER BY id LIMIT 1")
    ResumeRevisionEntity findByProposal(@Param("proposalId") Long proposalId);

    @Select("""
        SELECT * FROM resume_revision
        WHERE resume_id = #{resumeId}
        ORDER BY revision_number
        """)
    List<ResumeRevisionEntity> listByResume(@Param("resumeId") Long resumeId);

    @Select("SELECT COALESCE(MAX(revision_number), 0) FROM resume_revision WHERE resume_id = #{resumeId}")
    int maxRevisionNumber(@Param("resumeId") Long resumeId);
}
