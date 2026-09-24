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

    /**
     * The same row, read as of now rather than as of the caller's snapshot. MySQL's default
     * isolation freezes a transaction's plain reads at its first one, so an appender whose
     * transaction had already looked at the session would number itself from a "last message"
     * that a concurrent commit has since moved past. A locking read is a current read.
     */
    default InterviewMessageEntity findLatestForAppend(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewMessageEntity>()
            .eq(InterviewMessageEntity::getSessionId, sessionId)
            .orderByDesc(InterviewMessageEntity::getSeqNum)
            .last("LIMIT 1 FOR UPDATE"));
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
