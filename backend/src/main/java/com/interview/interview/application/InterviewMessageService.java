package com.interview.interview.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.application.port.InterviewMessageRepository;
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

    private final InterviewMessageRepository interviewMessageRepository;

    public InterviewMessage insertMessage(Long sessionId, String role, String content) {
        Object lock = SESSION_LOCKS.get(sessionId.toString(), ignored -> new Object());
        synchronized (lock) {
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setSeqNum(nextSeqNum(sessionId));
            interviewMessageRepository.add(message);
            return message;
        }
    }

    public void invalidateSessionLock(Long sessionId) {
        if (sessionId != null) {
            SESSION_LOCKS.invalidate(sessionId.toString());
        }
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageRepository.findLatest(sessionId);
        Integer seqNum = latest == null ? null : latest.getSeqNum();
        return seqNum == null ? 0 : seqNum + 1;
    }
}
