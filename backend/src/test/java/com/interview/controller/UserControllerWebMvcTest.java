package com.interview.controller;

import com.interview.common.GlobalExceptionHandler;
import com.interview.config.JwtInterceptor;
import com.interview.dto.LlmConfigTestResponse;
import com.interview.dto.LlmModelDiscoveryRequest;
import com.interview.dto.LlmModelDiscoveryResponse;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserProfileService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerWebMvcTest {

    @Mock private UserLlmConfigService userLlmConfigService;
    @Mock private UserProfileService userProfileService;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(jwtUtil.parseUserId("token")).thenReturn(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userLlmConfigService, userProfileService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator())
            .addInterceptors(new JwtInterceptor(jwtUtil))
            .build();
    }

    @Test
    void getLlmConfigRequiresAuthorizationAndReturnsConfig() throws Exception {
        when(userLlmConfigService.getCurrentUserConfig()).thenReturn(new UserLlmConfigResponse(
            "openai-compatible", "https://example.com/v1", "model-a", true, "****1234", 1024, "low"
        ));

        mockMvc.perform(get("/api/user/llm-config").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.providerKey").value("openai-compatible"))
            .andExpect(jsonPath("$.data.model").value("model-a"));
    }

    @Test
    void getLlmConfigRejectsMissingAuthorization() throws Exception {
        mockMvc.perform(get("/api/user/llm-config"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void updateLlmConfigValidatesBodyAndPassesRequestToService() throws Exception {
        when(userLlmConfigService.updateCurrentUserConfig(any())).thenReturn(new UserLlmConfigResponse(
            "openai-compatible", "https://example.com/v1", "model-a", true, "****1234", 1024, null
        ));

        mockMvc.perform(put("/api/user/llm-config")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerKey\":\"openai-compatible\",\"baseUrl\":\"https://example.com/v1\",\"model\":\"model-a\",\"apiKey\":\"sk-new\",\"maxTokens\":1024}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<UserLlmConfigRequest> captor = ArgumentCaptor.forClass(UserLlmConfigRequest.class);
        verify(userLlmConfigService).updateCurrentUserConfig(captor.capture());
        assertThat(captor.getValue().baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(captor.getValue().apiKey()).isEqualTo("sk-new");
    }

    @Test
    void updateLlmConfigRejectsMissingProvider() throws Exception {
        mockMvc.perform(put("/api/user/llm-config")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"model-a\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void testLlmConfigAllowsEmptyBodyAndPassesNullRequest() throws Exception {
        when(userLlmConfigService.testConfig(isNull())).thenReturn(new LlmConfigTestResponse("openai", "gpt-4o", true, "ok"));

        mockMvc.perform(post("/api/user/llm-config/test").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ok").value(true));

        verify(userLlmConfigService).testConfig(isNull());
    }

    @Test
    void discoverModelsValidatesBaseUrlAndPassesRequestToService() throws Exception {
        when(userLlmConfigService.discoverModels(any())).thenReturn(new LlmModelDiscoveryResponse(
            "https://example.com/v1", "sk-test", List.of("model-a")
        ));

        mockMvc.perform(post("/api/user/llm-config/discover-models")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseUrl\":\"https://example.com/v1\",\"apiKey\":\"sk-test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.models[0]").value("model-a"));

        ArgumentCaptor<LlmModelDiscoveryRequest> captor = ArgumentCaptor.forClass(LlmModelDiscoveryRequest.class);
        verify(userLlmConfigService).discoverModels(captor.capture());
        assertThat(captor.getValue().baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(captor.getValue().apiKey()).isEqualTo("sk-test");
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
