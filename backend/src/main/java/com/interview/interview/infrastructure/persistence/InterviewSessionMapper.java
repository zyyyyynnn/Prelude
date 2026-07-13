package com.interview.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.port.InterviewSessionRepository;

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
    default InterviewSession findOngoing(Long userId, Long resumeId, Long positionId) {
        return selectOne(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .eq(InterviewSession::getResumeId, resumeId)
            .eq(InterviewSession::getPositionId, positionId)
            .eq(InterviewSession::getStatus, "ongoing")
            .orderByDesc(InterviewSession::getCreatedAt)
            .last("LIMIT 1"));
    }

    @Override
    default List<InterviewSession> listByUser(Long userId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .orderByDesc(InterviewSession::getCreatedAt));
    }
}
