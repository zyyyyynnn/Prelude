package com.interview.service;

import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    InterviewStartResponse start(InterviewStartRequest request);

    List<InterviewSessionItemResponse> listCurrentUserSessions();

    SseEmitter chat(Long sessionId, InterviewChatRequest request);

    InterviewFinishResponse finish(Long sessionId);
}
