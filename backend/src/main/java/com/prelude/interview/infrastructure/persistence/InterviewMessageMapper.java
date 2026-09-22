package com.prelude.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

public interface InterviewMessageMapper extends BaseMapper<InterviewMessageEntity> {

    default InterviewMessageEntity findLatest(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewMessageEntity>()
            .eq(InterviewMessageEntity::getSessionId, sessionId)
            .orderByDesc(InterviewMessageEntity::getSeqNum)
            .last("LIMIT 1"));
    }

    default List<InterviewMessageEntity> listBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapper<InterviewMessageEntity>()
            .eq(InterviewMessageEntity::getSessionId, sessionId)
            .orderByAsc(InterviewMessageEntity::getSeqNum));
    }

    default long countConversationMessages(Long sessionId) {
        return selectCount(new LambdaQueryWrapper<InterviewMessageEntity>()
            .eq(InterviewMessageEntity::getSessionId, sessionId)
            .in(InterviewMessageEntity::getRole, "user", "assistant"));
    }
}
