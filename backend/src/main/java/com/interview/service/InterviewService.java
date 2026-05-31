package com.interview.service;

import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewMessagesResponse;
import com.interview.dto.InterviewStageUpdateRequest;
import com.interview.dto.InterviewStageUpdateResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    InterviewStartResponse start(InterviewStartRequest request);

    List<InterviewSessionItemResponse> listCurrentUserSessions();

    InterviewMessagesResponse getSessionMessages(Long sessionId);

    InterviewStageUpdateResponse updateStage(Long sessionId, InterviewStageUpdateRequest request);

    SseEmitter chat(Long sessionId, InterviewChatRequest request, boolean autoStart);

    InterviewFinishResponse finish(Long sessionId);
}
