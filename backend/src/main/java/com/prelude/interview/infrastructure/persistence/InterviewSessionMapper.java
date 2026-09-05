package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface InterviewSessionMapper extends BaseMapper<InterviewSession>, InterviewSessionRepository {

    @Override
    default int add(InterviewSession session) {
        return insert(session);
    }

    @Override
    default int update(InterviewSession session) {
        return updateById(session);
    }

    @Override
    @Update("""
        UPDATE interview_session
        SET status = 'generating'
        WHERE id = #{sessionId}
          AND account_id = #{accountId}
          AND status = 'ongoing'
        """)
    int markGeneratingIfOngoing(@Param("sessionId") Long sessionId, @Param("accountId") Long accountId);

    @Update("""
        UPDATE interview_session
        SET status = 'finished', summary_report = #{reportJson}
        WHERE id = #{sessionId}
          AND status = 'generating'
        """)
    int completeReportIfGenerating(
        @Param("sessionId") Long sessionId,
        @Param("reportJson") String reportJson
    );

    @Override
    default List<InterviewSession> listByUser(Long accountId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getAccountId, accountId)
            .orderByDesc(InterviewSession::getCreatedAt));
    }
}
