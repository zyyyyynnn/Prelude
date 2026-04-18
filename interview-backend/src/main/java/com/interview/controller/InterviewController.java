package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import com.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    public Result<InterviewStartResponse> start(@Valid @RequestBody InterviewStartRequest request) {
        return Result.success(interviewService.start(request));
    }

    @GetMapping("/sessions")
    public Result<List<InterviewSessionItemResponse>> sessions() {
        return Result.success(interviewService.listCurrentUserSessions());
    }

    @PostMapping("/{sessionId}/chat")
    public SseEmitter chat(
        @PathVariable Long sessionId,
        @Valid @RequestBody InterviewChatRequest request
    ) {
        return interviewService.chat(sessionId, request);
    }

    @PostMapping("/{sessionId}/finish")
    public Result<InterviewFinishResponse> finish(@PathVariable Long sessionId) {
        return Result.success(interviewService.finish(sessionId));
    }
}
