package com.prelude.interview.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.interview.application.DeleteInterviewSession;
import com.prelude.interview.application.FinishInterview;
import com.prelude.interview.application.FinishInterviewResult;
import com.prelude.interview.application.InterviewAttachmentView;
import com.prelude.interview.application.InterviewMessageView;
import com.prelude.interview.application.InterviewSessionDetails;
import com.prelude.interview.application.InterviewSessionQueryService;
import com.prelude.interview.application.InterviewSessionSummary;
import com.prelude.interview.application.InterviewStageView;
import com.prelude.interview.application.ListenInterview;
import com.prelude.interview.application.PinInterviewSession;
import com.prelude.interview.application.StartInterview;
import com.prelude.interview.application.StartInterviewCommand;
import com.prelude.interview.application.StartInterviewResult;
import com.prelude.interview.application.StreamChatTurn;
import com.prelude.interview.application.UpdateInterviewStage;
import com.prelude.interview.application.UpdateInterviewStageResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterviewControllerTest {

    private final StartInterview startInterview = mock(StartInterview.class);
    private final InterviewSessionQueryService sessionQueryService =
        mock(InterviewSessionQueryService.class);
    private final UpdateInterviewStage updateInterviewStage = mock(UpdateInterviewStage.class);
    private final StreamChatTurn streamChatTurn = mock(StreamChatTurn.class);
    private final FinishInterview finishInterview = mock(FinishInterview.class);
    private final ListenInterview listenInterview = mock(ListenInterview.class);
    private final PinInterviewSession pinInterviewSession = mock(PinInterviewSession.class);
    private final DeleteInterviewSession deleteInterviewSession = mock(DeleteInterviewSession.class);
    private final com.prelude.activity.RealtimePort realtimePort =
        mock(com.prelude.activity.RealtimePort.class);
    private final com.prelude.activity.RealtimeConnection connection =
        mock(com.prelude.activity.RealtimeConnection.class);
    private final java.util.concurrent.ScheduledExecutorService heartbeatExecutor =
        mock(java.util.concurrent.ScheduledExecutorService.class);

    @org.junit.jupiter.api.BeforeEach
    void stubTheStreamCollaborators() {
        when(realtimePort.register(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(connection);
        // The real stream schedules a heartbeat; without a future, complete() cannot cancel it.
        when(heartbeatExecutor.scheduleAtFixedRate(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class));
    }

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new InterviewController(
            startInterview,
            sessionQueryService,
            updateInterviewStage,
            streamChatTurn,
            finishInterview,
            listenInterview,
            pinInterviewSession,
            deleteInterviewSession,
            realtimePort,
            heartbeatExecutor
        ))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void startForwardsEveryDecodedFieldIntoTheCommand() throws Exception {
        when(startInterview.execute(new StartInterviewCommand(3L, 5L, "jd", "deepseek-v4-pro", List.of(8L))))
            .thenReturn(new StartInterviewResult(77L, "Java 后端工程师", "warmup"));

        mockMvc.perform(post("/api/interview/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"resumeId":3,"positionId":5,"jdText":"jd","requestedModel":"deepseek-v4-pro",\
                    "attachmentIds":[8]}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.sessionId").value(77))
            .andExpect(jsonPath("$.data.targetPosition").value("Java 后端工程师"))
            .andExpect(jsonPath("$.data.currentStage").value("warmup"));
    }

    @Test
    void startRejectsAMissingResumeBeforeReachingTheUseCase() throws Exception {
        mockMvc.perform(post("/api/interview/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionId\":5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(startInterview);
    }

    @Test
    void startRejectsAMissingPositionBeforeReachingTheUseCase() throws Exception {
        mockMvc.perform(post("/api/interview/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(startInterview);
    }

    @Test
    void startReportsTheBusinessProblemFromAnUnknownPosition() throws Exception {
        when(startInterview.execute(any())).thenThrow(BusinessException.badRequest("岗位模板不存在"));

        mockMvc.perform(post("/api/interview/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":3,\"positionId\":999}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("岗位模板不存在"));
    }

    @Test
    void sessionsKeepsTheServerOrderAndCarriesThePinnedFlag() throws Exception {
        when(sessionQueryService.listCurrentUserSessions()).thenReturn(List.of(
            new InterviewSessionSummary(2L, "Java 后端工程师", "ongoing", null, "technical", null, true),
            new InterviewSessionSummary(1L, "前端工程师", "finished", null, "closing", "报告", false)
        ));

        mockMvc.perform(get("/api/interview/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionId").value(2))
            .andExpect(jsonPath("$.data[0].pinned").value(true))
            .andExpect(jsonPath("$.data[1].sessionId").value(1))
            .andExpect(jsonPath("$.data[1].pinned").value(false))
            .andExpect(jsonPath("$.data[1].summaryReport").value("报告"));
    }

    @Test
    void messagesMapsStagesMessagesAndAttachmentsIntoTheEnvelope() throws Exception {
        when(sessionQueryService.getSessionMessages(41L)).thenReturn(new InterviewSessionDetails(
            41L,
            "Java 后端工程师",
            "ongoing",
            "technical",
            "deepseek-v4-pro",
            "HIGH",
            null,
            List.of(new InterviewStageView("warmup", LocalDateTime.parse("2026-09-01T08:00:00"), null)),
            List.of(new InterviewMessageView(9L, "assistant", "请先介绍一下你自己。", 2, null, null, null)),
            3L,
            5L,
            "jd",
            List.of(new InterviewAttachmentView(8L, "补充材料.pdf", "application/pdf", 2048L, false))
        ));

        mockMvc.perform(get("/api/interview/41/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.model").value("deepseek-v4-pro"))
            .andExpect(jsonPath("$.data.reasoningLevel").value("HIGH"))
            .andExpect(jsonPath("$.data.stages[0].stageName").value("warmup"))
            .andExpect(jsonPath("$.data.stages[0].endedAt").doesNotExist())
            .andExpect(jsonPath("$.data.messages[0].content").value("请先介绍一下你自己。"))
            .andExpect(jsonPath("$.data.attachments[0].fileName").value("补充材料.pdf"))
            .andExpect(jsonPath("$.data.attachments[0].image").value(false));
    }

    @Test
    void stageUpdateUsesThePathSessionAndTheDecodedStageName() throws Exception {
        when(updateInterviewStage.execute(41L, "deep_dive"))
            .thenReturn(new UpdateInterviewStageResult("deep_dive", LocalDateTime.parse("2026-09-01T09:30:00")));

        mockMvc.perform(post("/api/interview/41/stage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stageName\":\"deep_dive\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stageName").value("deep_dive"));

        verify(updateInterviewStage).execute(41L, "deep_dive");
    }

    @Test
    void stageUpdateRejectsABlankStageNameBeforeReachingTheUseCase() throws Exception {
        mockMvc.perform(post("/api/interview/41/stage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stageName\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(updateInterviewStage);
    }

    @Test
    void chatPassesTheAuthenticatedSessionIdAndTheAutoStartFlag() throws Exception {
        var session = new MockHttpSession();

        mockMvc.perform(post("/api/interview/41/chat")
                .session(session)
                .param("autoStart", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"我的回答\"}"))
            .andExpect(status().isOk());

        verify(streamChatTurn).execute(eq(41L), eq("我的回答"), eq(true), eq(session.getId()), any(com.prelude.activity.SseSessionStream.class));
    }

    @Test
    void chatWithoutASessionReachesTheUseCaseWithNoAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/interview/41/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"我的回答\"}"))
            .andExpect(status().isOk());

        verify(streamChatTurn).execute(eq(41L), eq("我的回答"), eq(false), isNull(), any(com.prelude.activity.SseSessionStream.class));
    }

    /* A missing `content` is deliberately not rejected here: auto-start opens a session by
       posting an empty turn, and only `RunInterviewTurn` can tell that case from an empty
       answer inside an existing conversation. This locks the boundary so a future
       `@NotBlank` on the DTO cannot silently kill the opening question. */
    @Test
    void chatForwardsABodyWithoutContentSoAutoStartReachesTheUseCase() throws Exception {
        mockMvc.perform(post("/api/interview/41/chat")
                .param("autoStart", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        verify(streamChatTurn).execute(eq(41L), isNull(), eq(true), isNull(), any(com.prelude.activity.SseSessionStream.class));
    }

    @Test
    void finishReturnsTheQueuedJobReference() throws Exception {
        when(finishInterview.execute(41L))
            .thenReturn(new FinishInterviewResult(41L, null, "finished", "job-7"));

        mockMvc.perform(post("/api/interview/41/finish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(41))
            .andExpect(jsonPath("$.data.status").value("finished"))
            .andExpect(jsonPath("$.data.jobId").value("job-7"))
            .andExpect(jsonPath("$.data.summaryReport").doesNotExist());
    }

    @Test
    void pinWritesTheDecodedFlagAgainstThePathSession() throws Exception {
        mockMvc.perform(patch("/api/interview/41/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(pinInterviewSession).execute(41L, true);
    }

    @Test
    void pinReportsTheOwnershipProblemWithoutWriting() throws Exception {
        doThrow(BusinessException.badRequest("会话不存在或不属于当前账号"))
            .when(pinInterviewSession).execute(eq(41L), anyBoolean());

        mockMvc.perform(patch("/api/interview/41/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void deleteUsesThePathSessionAsTheOwnershipScope() throws Exception {
        mockMvc.perform(delete("/api/interview/41"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(deleteInterviewSession).execute(41L);
    }

    @Test
    void listenPassesTheAuthenticatedSessionId() throws Exception {
        var session = new MockHttpSession();

        mockMvc.perform(get("/api/interview/41/listen").session(session))
            .andExpect(status().isOk());

        verify(listenInterview).execute(eq(41L), eq(session.getId()), any(com.prelude.activity.SseSessionStream.class));
    }

    @Test
    void listenWithoutASessionStillReachesTheUseCaseSoItCanReportTheProblem() throws Exception {
        mockMvc.perform(get("/api/interview/41/listen")).andExpect(status().isOk());

        verify(listenInterview).execute(eq(41L), isNull(), any(com.prelude.activity.SseSessionStream.class));
    }
}
