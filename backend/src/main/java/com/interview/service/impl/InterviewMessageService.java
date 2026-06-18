package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.interview.entity.InterviewMessage;
import com.interview.mapper.InterviewMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private static final Cache<String, Object> SESSION_LOCKS = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(30))
        .maximumSize(10_000)
        .build();

    private final InterviewMessageMapper interviewMessageMapper;

    public InterviewMessage insertMessage(Long sessionId, String role, String content) {
        Object lock = SESSION_LOCKS.get(sessionId.toString(), ignored -> new Object());
        synchronized (lock) {
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setSeqNum(nextSeqNum(sessionId));
            interviewMessageMapper.insert(message);
            return message;
        }
    }

    public void invalidateSessionLock(Long sessionId) {
        if (sessionId != null) {
            SESSION_LOCKS.invalidate(sessionId.toString());
        }
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByDesc(InterviewMessage::getSeqNum)
            .last("LIMIT 1"));
        Integer seqNum = latest == null ? null : latest.getSeqNum();
        return seqNum == null ? 0 : seqNum + 1;
    }
}
