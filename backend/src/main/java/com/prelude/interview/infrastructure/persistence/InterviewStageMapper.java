package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

public interface InterviewStageMapper extends BaseMapper<InterviewStageEntity> {

    default InterviewStageEntity findCurrent(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewStageEntity>()
            .eq(InterviewStageEntity::getSessionId, sessionId)
            .isNull(InterviewStageEntity::getEndedAt)
            .orderByDesc(InterviewStageEntity::getStartedAt)
            .last("LIMIT 1"));
    }

    default InterviewStageEntity findLatest(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewStageEntity>()
            .eq(InterviewStageEntity::getSessionId, sessionId)
            .orderByDesc(InterviewStageEntity::getStartedAt)
            .last("LIMIT 1"));
    }

    default List<InterviewStageEntity> listBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapper<InterviewStageEntity>()
            .eq(InterviewStageEntity::getSessionId, sessionId)
            .orderByAsc(InterviewStageEntity::getStartedAt)
            .orderByAsc(InterviewStageEntity::getId));
    }
}
