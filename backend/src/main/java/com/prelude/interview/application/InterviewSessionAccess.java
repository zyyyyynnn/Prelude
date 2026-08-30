package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.UserContext;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewSessionAccess {

    private static final String STATUS_ONGOING = "ongoing";

    private final InterviewSessionRepository interviewSessionRepository;

    public Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }

    public InterviewSession requireOwned(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw BusinessException.badRequest("面试会话不存在或无权访问");
        }
        return session;
    }

    public InterviewSession requireOngoing(Long sessionId, Long userId) {
        InterviewSession session = requireOwned(sessionId, userId);
        if (!STATUS_ONGOING.equals(session.getStatus())) {
            throw BusinessException.badRequest("面试会话已结束");
        }
        return session;
    }
}
