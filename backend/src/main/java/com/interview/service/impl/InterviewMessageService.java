package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewMessage;
import com.interview.mapper.InterviewMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private final InterviewMessageMapper interviewMessageMapper;

    public InterviewMessage insertMessage(Long sessionId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(nextSeqNum(sessionId));
        interviewMessageMapper.insert(message);
        return message;
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
