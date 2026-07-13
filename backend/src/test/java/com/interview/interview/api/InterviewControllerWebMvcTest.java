package com.interview.interview.api;

import com.interview.shared.web.GlobalExceptionHandler;
import com.interview.shared.web.JwtInterceptor;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.FinishInterviewResult;
import com.interview.interview.application.InterviewMessageView;
import com.interview.interview.application.InterviewSessionDetails;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.InterviewSessionSummary;
import com.interview.interview.application.InterviewStageView;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StartInterviewCommand;
import com.interview.interview.application.StartInterviewResult;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
import com.interview.interview.application.UpdateInterviewStageResult;
import com.interview.platform.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InterviewControllerWebMvcTest {

    @Mock private StartInterview startInterview;
    @Mock private InterviewSessionQueryService sessionQueryService;
    @Mock private UpdateInterviewStage updateInterviewStage;
    @Mock private StreamChatTurn streamChatTurn;
    @Mock private FinishInterview finishInterview;
    @Mock private ListenInterview listenInterview;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(jwtUtil.parseUserId("token")).thenReturn(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(new InterviewController(
                startInterview,
                sessionQueryService,
                updateInterviewStage,
                streamChatTurn,
                finishInterview,
                listenInterview
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator())
            .addInterceptors(new JwtInterceptor(jwtUtil))
            .build();
    }

    @Test
    void startValidatesRequestAndCallsService() throws Exception {
        when(startInterview.execute(any())).thenReturn(new StartInterviewResult(7L, "Java 后端工程师", "warmup"));

        mockMvc.perform(post("/api/interview/start")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":1,\"positionId\":2,\"jdText\":\"JD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7));

        ArgumentCaptor<StartInterviewCommand> captor = ArgumentCaptor.forClass(StartInterviewCommand.class);
        verify(startInterview).execute(captor.capture());
        assertThat(captor.getValue().resumeId()).isEqualTo(1L);
        assertThat(captor.getValue().positionId()).isEqualTo(2L);
    }

    @Test
    void startRejectsMissingResume() throws Exception {
        mockMvc.perform(post("/api/interview/start")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionId\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void sessionsRequireAuthorization() throws Exception {
        mockMvc.perform(get("/api/interview/sessions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void sessionsMapApplicationViewsToApiResponses() throws Exception {
        when(sessionQueryService.listCurrentUserSessions()).thenReturn(List.of(
            new InterviewSessionSummary(
                7L, "Java", "ongoing", LocalDateTime.of(2026, 7, 13, 10, 0),
                "technical", "openai", "gpt", null
            )
        ));

        mockMvc.perform(get("/api/interview/sessions").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionId").value(7))
            .andExpect(jsonPath("$.data[0].currentStage").value("technical"));
    }

    @Test
    void listMessagesDelegatesToService() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 13, 10, 0);
        when(sessionQueryService.getSessionMessages(7L)).thenReturn(new InterviewSessionDetails(
            7L,
            "Java",
            "ongoing",
            "warmup",
            null,
            List.of(new InterviewStageView("warmup", timestamp, null)),
            List.of(new InterviewMessageView(11L, "user", "回答", 0, timestamp, 8, "提示")),
            1L,
            2L,
            "JD"
        ));

        mockMvc.perform(get("/api/interview/7/messages").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7))
            .andExpect(jsonPath("$.data.stages[0].stageName").value("warmup"))
            .andExpect(jsonPath("$.data.messages[0].id").value(11))
            .andExpect(jsonPath("$.data.messages[0].hint").value("提示"));

        verify(sessionQueryService).getSessionMessages(7L);
    }

    @Test
    void chatPassesAutoStartAndRequestToService() throws Exception {
        when(streamChatTurn.execute(any(), any(), anyBoolean())).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/interview/7/chat?autoStart=true")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"回答\"}"))
            .andExpect(status().isOk());

        verify(streamChatTurn).execute(7L, "回答", true);
    }

    @Test
    void stageMapsRequestAndApplicationResult() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 13, 10, 0);
        when(updateInterviewStage.execute(7L, "technical"))
            .thenReturn(new UpdateInterviewStageResult("technical", startedAt));

        mockMvc.perform(post("/api/interview/7/stage")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stageName\":\"technical\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stageName").value("technical"));

        verify(updateInterviewStage).execute(7L, "technical");
    }

    @Test
    void chatRejectsOversizedContent() throws Exception {
        String content = "a".repeat(4001);

        mockMvc.perform(post("/api/interview/7/chat")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void listenDelegatesToService() throws Exception {
        when(listenInterview.execute(7L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/interview/7/listen").header("Authorization", "Bearer token"))
            .andExpect(status().isOk());

        verify(listenInterview).execute(7L);
    }

    @Test
    void finishReturnsGeneratingStatus() throws Exception {
        when(finishInterview.execute(7L)).thenReturn(new FinishInterviewResult(7L, null, "generating", "job-1"));

        mockMvc.perform(post("/api/interview/7/finish").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("generating"))
            .andExpect(jsonPath("$.data.jobId").value("job-1"));
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
