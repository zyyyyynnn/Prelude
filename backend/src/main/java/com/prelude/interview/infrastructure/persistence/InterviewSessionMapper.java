package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;

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
    default List<InterviewSession> listByUser(Long userId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getUserId, userId)
            .orderByDesc(InterviewSession::getCreatedAt));
    }
}
