package com.interview.interview.api;

import com.interview.shared.web.GlobalExceptionHandler;
import com.interview.shared.web.JwtInterceptor;
import com.interview.interview.api.InterviewChatRequest;
import com.interview.interview.api.InterviewFinishResponse;
import com.interview.interview.api.InterviewMessagesResponse;
import com.interview.interview.api.InterviewStartRequest;
import com.interview.interview.api.InterviewStartResponse;
import com.interview.interview.application.FinishInterview;
import com.interview.interview.application.InterviewSessionQueryService;
import com.interview.interview.application.ListenInterview;
import com.interview.interview.application.StartInterview;
import com.interview.interview.application.StreamChatTurn;
import com.interview.interview.application.UpdateInterviewStage;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
        when(startInterview.execute(any())).thenReturn(new InterviewStartResponse(7L, "Java 后端工程师", "warmup"));

        mockMvc.perform(post("/api/interview/start")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":1,\"positionId\":2,\"jdText\":\"JD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7));

        ArgumentCaptor<InterviewStartRequest> captor = ArgumentCaptor.forClass(InterviewStartRequest.class);
        verify(startInterview).execute(captor.capture());
        assertThat(captor.getValue().getResumeId()).isEqualTo(1L);
        assertThat(captor.getValue().getPositionId()).isEqualTo(2L);
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
    void listMessagesDelegatesToService() throws Exception {
        when(sessionQueryService.getSessionMessages(7L)).thenReturn(new InterviewMessagesResponse(
            7L, "Java", "ongoing", "warmup", null, List.of(), List.of(), 1L, 2L, "JD"
        ));

        mockMvc.perform(get("/api/interview/7/messages").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7));

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

        ArgumentCaptor<InterviewChatRequest> requestCaptor = ArgumentCaptor.forClass(InterviewChatRequest.class);
        verify(streamChatTurn).execute(eq(7L), requestCaptor.capture(), eq(true));
        assertThat(requestCaptor.getValue().getContent()).isEqualTo("回答");
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
        when(finishInterview.execute(7L)).thenReturn(new InterviewFinishResponse(7L, null, "generating", "job-1"));

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
