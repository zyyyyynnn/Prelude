package com.interview.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.application.port.InterviewStageRepository;

import java.util.List;

public interface InterviewStageMapper extends BaseMapper<InterviewStage>, InterviewStageRepository {

    @Override
    default int add(InterviewStage stage) {
        return insert(stage);
    }

    @Override
    default int update(InterviewStage stage) {
        return updateById(stage);
    }

    @Override
    default InterviewStage findCurrent(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .isNull(InterviewStage::getEndedAt)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
    }

    @Override
    default InterviewStage findLatest(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
    }

    @Override
    default List<InterviewStage> listBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .orderByAsc(InterviewStage::getStartedAt)
            .orderByAsc(InterviewStage::getId));
    }
}
