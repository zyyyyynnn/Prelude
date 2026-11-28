package com.prelude.interview.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.domain.AssessmentStatus;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.infrastructure.persistence.InterviewMessageEntity;
import com.prelude.interview.infrastructure.persistence.InterviewMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public int updateAssessmentIfPending(InterviewMessage message) {
        return messageMapper.update(null, new LambdaUpdateWrapper<InterviewMessageEntity>()
            .set(InterviewMessageEntity::getScore, message.getScore())
            .set(InterviewMessageEntity::getHint, message.getHint())
            .set(InterviewMessageEntity::getAssessmentStatus, message.getAssessmentStatus())
            .set(InterviewMessageEntity::getAssessmentError, message.getAssessmentError())
            .eq(InterviewMessageEntity::getId, message.getId())
            .eq(InterviewMessageEntity::getAssessmentStatus, AssessmentStatus.PENDING)
            .isNull(InterviewMessageEntity::getSupersededByMessageId));
    }

    @Override
    public List<InterviewMessage> findPendingForJudge(LocalDateTime cutoff, int limit) {
        return messageMapper.findPendingForJudge(cutoff, limit).stream()
            .map(InterviewMessageEntity::toDomain)
            .toList();
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
    public InterviewMessage findByReplyToMessageId(Long userMessageId) {
        InterviewMessageEntity entity = messageMapper.findByReplyToMessageId(userMessageId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public int markSuperseded(Long messageId, Long supersedingMessageId) {
        return messageMapper.markSuperseded(messageId, supersedingMessageId);
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

    @Override
    public long countPendingAssessments(Long sessionId) {
        return messageMapper.countPendingAssessments(sessionId);
    }
}
