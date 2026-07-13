package com.interview.interview.api;

import com.interview.shared.api.Result;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final StartInterview startInterview;
    private final InterviewSessionQueryService sessionQueryService;
    private final UpdateInterviewStage updateInterviewStage;
    private final StreamChatTurn streamChatTurn;
    private final FinishInterview finishInterview;
    private final ListenInterview listenInterview;

    @PostMapping("/start")
    public Result<InterviewStartResponse> start(@Valid @RequestBody InterviewStartRequest request) {
        return Result.success(InterviewApiMapper.toResponse(
            startInterview.execute(InterviewApiMapper.toCommand(request))
        ));
    }

    @GetMapping("/sessions")
    public Result<List<InterviewSessionItemResponse>> sessions() {
        return Result.success(sessionQueryService.listCurrentUserSessions().stream()
            .map(InterviewApiMapper::toResponse)
            .toList());
    }

    @GetMapping("/{sessionId}/messages")
    public Result<InterviewMessagesResponse> messages(@PathVariable Long sessionId) {
        return Result.success(InterviewApiMapper.toResponse(
            sessionQueryService.getSessionMessages(sessionId)
        ));
    }

    @PostMapping("/{sessionId}/stage")
    public Result<InterviewStageUpdateResponse> stage(
        @PathVariable Long sessionId,
        @Valid @RequestBody InterviewStageUpdateRequest request
    ) {
        return Result.success(InterviewApiMapper.toResponse(
            updateInterviewStage.execute(sessionId, request.stageName())
        ));
    }

    @PostMapping("/{sessionId}/chat")
    public SseEmitter chat(
        @PathVariable Long sessionId,
        @Valid @RequestBody InterviewChatRequest request,
        @RequestParam(defaultValue = "false") boolean autoStart
    ) {
        return streamChatTurn.execute(sessionId, request.getContent(), autoStart);
    }

    @PostMapping("/{sessionId}/finish")
    public Result<InterviewFinishResponse> finish(@PathVariable Long sessionId) {
        return Result.success(InterviewApiMapper.toResponse(finishInterview.execute(sessionId)));
    }

    @GetMapping(value = "/{sessionId}/listen", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter listen(@PathVariable Long sessionId) {
        return listenInterview.execute(sessionId);
    }
}
