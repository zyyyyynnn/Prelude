package com.interview.controller;

import com.interview.common.GlobalExceptionHandler;
import com.interview.config.JwtInterceptor;
import com.interview.dto.InterviewChatRequest;
import com.interview.dto.InterviewFinishResponse;
import com.interview.dto.InterviewMessagesResponse;
import com.interview.dto.InterviewStartRequest;
import com.interview.dto.InterviewStartResponse;
import com.interview.service.InterviewService;
import com.interview.util.JwtUtil;
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

    @Mock private InterviewService interviewService;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(jwtUtil.parseUserId("token")).thenReturn(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(new InterviewController(interviewService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator())
            .addInterceptors(new JwtInterceptor(jwtUtil))
            .build();
    }

    @Test
    void startValidatesRequestAndCallsService() throws Exception {
        when(interviewService.start(any())).thenReturn(new InterviewStartResponse(7L, "Java 后端工程师", "warmup"));

        mockMvc.perform(post("/api/interview/start")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":1,\"positionId\":2,\"jdText\":\"JD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7));

        ArgumentCaptor<InterviewStartRequest> captor = ArgumentCaptor.forClass(InterviewStartRequest.class);
        verify(interviewService).start(captor.capture());
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
        when(interviewService.getSessionMessages(7L)).thenReturn(new InterviewMessagesResponse(
            7L, "Java", "ongoing", "warmup", null, List.of(), List.of(), 1L, 2L, "JD"
        ));

        mockMvc.perform(get("/api/interview/7/messages").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionId").value(7));

        verify(interviewService).getSessionMessages(7L);
    }

    @Test
    void chatPassesAutoStartAndRequestToService() throws Exception {
        when(interviewService.chat(any(), any(), anyBoolean())).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/interview/7/chat?autoStart=true")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"回答\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<InterviewChatRequest> requestCaptor = ArgumentCaptor.forClass(InterviewChatRequest.class);
        verify(interviewService).chat(eq(7L), requestCaptor.capture(), eq(true));
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
        when(interviewService.listen(7L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/interview/7/listen").header("Authorization", "Bearer token"))
            .andExpect(status().isOk());

        verify(interviewService).listen(7L);
    }

    @Test
    void finishReturnsGeneratingStatus() throws Exception {
        when(interviewService.finish(7L)).thenReturn(new InterviewFinishResponse(7L, null, "generating", "job-1"));

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
