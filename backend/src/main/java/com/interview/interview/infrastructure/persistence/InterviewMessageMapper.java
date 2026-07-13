package com.interview.interview.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.application.port.InterviewMessageRepository;

import java.util.List;

public interface InterviewMessageMapper extends BaseMapper<InterviewMessage>, InterviewMessageRepository {

    @Override
    default int add(InterviewMessage message) {
        return insert(message);
    }

    @Override
    default int update(InterviewMessage message) {
        return updateById(message);
    }

    @Override
    default int delete(java.io.Serializable messageId) {
        return deleteById(messageId);
    }

    @Override
    default InterviewMessage findLatest(Long sessionId) {
        return selectOne(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByDesc(InterviewMessage::getSeqNum)
            .last("LIMIT 1"));
    }

    @Override
    default List<InterviewMessage> listBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
    }

    @Override
    default long countConversationMessages(Long sessionId) {
        return selectCount(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .in(InterviewMessage::getRole, "user", "assistant"));
    }
}
