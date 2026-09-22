package com.prelude.interview.web;

import com.prelude.Result;
import com.prelude.activity.RealtimePort;
import com.prelude.activity.SseSessionStream;
import com.prelude.interview.api.InterviewChatRequest;
import com.prelude.interview.api.InterviewFinishResponse;
import com.prelude.interview.api.InterviewMessagesResponse;
import com.prelude.interview.api.InterviewPinRequest;
import com.prelude.interview.api.InterviewSessionItemResponse;
import com.prelude.interview.api.InterviewStageUpdateRequest;
import com.prelude.interview.api.InterviewStageUpdateResponse;
import com.prelude.interview.api.InterviewStartRequest;
import com.prelude.interview.api.InterviewStartResponse;
import com.prelude.interview.application.DeleteInterviewSession;
import com.prelude.interview.application.FinishInterview;
import com.prelude.interview.application.InterviewSessionQueryService;
import com.prelude.interview.application.ListenInterview;
import com.prelude.interview.application.PinInterviewSession;
import com.prelude.interview.application.StartInterview;
import com.prelude.interview.application.StreamChatTurn;
import com.prelude.interview.application.UpdateInterviewStage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final PinInterviewSession pinInterviewSession;
    private final DeleteInterviewSession deleteInterviewSession;
    private final RealtimePort realtimePort;
    @org.springframework.beans.factory.annotation.Qualifier("sseHeartbeatExecutor")
    private final java.util.concurrent.ScheduledExecutorService sseHeartbeatExecutor;

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
        @RequestParam(defaultValue = "false") boolean autoStart,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        SseSessionStream stream = SseSessionStream.open(realtimePort, sessionId, sseHeartbeatExecutor);
        try {
            streamChatTurn.execute(
                sessionId, request.getContent(), autoStart, authSessionId(servletRequest), stream);
        } catch (RuntimeException failure) {
            stream.complete();
            throw failure;
        }
        return stream.emitter();
    }

    @PostMapping("/{sessionId}/finish")
    public Result<InterviewFinishResponse> finish(@PathVariable Long sessionId) {
        return Result.success(InterviewApiMapper.toResponse(finishInterview.execute(sessionId)));
    }

    @PatchMapping("/{sessionId}/pin")
    public Result<Void> pin(
        @PathVariable Long sessionId,
        @Valid @RequestBody InterviewPinRequest request
    ) {
        pinInterviewSession.execute(sessionId, request.pinned());
        return Result.success(null);
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable Long sessionId) {
        deleteInterviewSession.execute(sessionId);
        return Result.success(null);
    }

    @GetMapping(value = "/{sessionId}/listen", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter listen(@PathVariable Long sessionId, jakarta.servlet.http.HttpServletRequest servletRequest) {
        SseSessionStream stream = SseSessionStream.open(realtimePort, sessionId, sseHeartbeatExecutor);
        try {
            listenInterview.execute(sessionId, authSessionId(servletRequest), stream);
        } catch (RuntimeException failure) {
            stream.complete();
            throw failure;
        }
        return stream.emitter();
    }

    private String authSessionId(jakarta.servlet.http.HttpServletRequest servletRequest) {
        var session = servletRequest.getSession(false);
        return session == null ? null : session.getId();
    }
}
