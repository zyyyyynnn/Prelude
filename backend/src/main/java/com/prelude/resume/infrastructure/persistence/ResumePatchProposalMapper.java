package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ResumePatchProposalMapper extends BaseMapper<ResumePatchProposalEntity> {

    /**
     * A decision lands only while the proposal is still pending. Two accepts in quick
     * succession therefore produce one revision, not two, and the loser of the race sees
     * zero rows affected rather than a silently duplicated patch.
     */
    @Update("""
        UPDATE resume_patch_proposal
        SET status = #{status}, decision_note = #{decisionNote},
            decided_at = #{now}, decided_by_account_id = #{accountId}
        WHERE id = #{proposalId} AND status = 'pending'
        """)
    int decide(
        @Param("proposalId") Long proposalId,
        @Param("status") String status,
        @Param("decisionNote") String decisionNote,
        @Param("accountId") Long accountId,
        @Param("now") LocalDateTime now
    );

    @Select("SELECT * FROM resume_patch_proposal WHERE id = #{proposalId} AND resume_id = #{resumeId}")
    ResumePatchProposalEntity findOwned(
        @Param("proposalId") Long proposalId,
        @Param("resumeId") Long resumeId
    );

    @Select("""
        <script>
        SELECT * FROM resume_patch_proposal
        WHERE conversation_id IN
        <foreach item="id" collection="conversationIds" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY id
        </script>
        """)
    List<ResumePatchProposalEntity> listByConversations(
        @Param("conversationIds") Collection<Long> conversationIds
    );

    @Select("""
        SELECT * FROM resume_patch_proposal
        WHERE resume_id = #{resumeId} AND base_revision < #{revision} AND status = 'pending'
        """)
    List<ResumePatchProposalEntity> listStaleCandidates(
        @Param("resumeId") Long resumeId,
        @Param("revision") int revision
    );
}
