package com.prelude.interview.infrastructure;

import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.infrastructure.persistence.InterviewMessageEntity;
import com.prelude.interview.infrastructure.persistence.InterviewMessageMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Adapts the message port to the row table, so the port never carries the row type. */
@Repository
@RequiredArgsConstructor
public class MybatisInterviewMessageRepository implements InterviewMessageRepository {

    private final InterviewMessageMapper messageMapper;

    @Override
    public int add(InterviewMessage message) {
        InterviewMessageEntity entity = InterviewMessageEntity.of(message);
        int rows = messageMapper.insert(entity);
        message.setId(entity.getId());
        return rows;
    }

    @Override
    public int update(InterviewMessage message) {
        return messageMapper.updateById(InterviewMessageEntity.of(message));
    }

    @Override
    public int delete(java.io.Serializable messageId) {
        return messageMapper.deleteById(messageId);
    }

    @Override
    public InterviewMessage findLatest(Long sessionId) {
        InterviewMessageEntity entity = messageMapper.findLatest(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public InterviewMessage findLatestForAppend(Long sessionId) {
        InterviewMessageEntity entity = messageMapper.findLatestForAppend(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public InterviewMessage findById(Long messageId) {
        InterviewMessageEntity entity = messageMapper.selectById(messageId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public List<InterviewMessage> listBySession(Long sessionId) {
        return messageMapper.listBySession(sessionId).stream()
            .map(InterviewMessageEntity::toDomain)
            .toList();
    }

    @Override
    public long countConversationMessages(Long sessionId) {
        return messageMapper.countConversationMessages(sessionId);
    }
}
