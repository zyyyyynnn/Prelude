package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.web.LlmController;
import com.prelude.test.AccountFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LlmControllerContractTest {

    @Test
    void providersEndpointExposesTheCanonicalProviderAndModelCapabilityShape() throws Exception {
        LlmPort llmPort = mock(LlmPort.class);
        var currentAccount = AccountFixtures.current(7L);
        when(llmPort.listModels()).thenReturn(List.of(
            LlmFixtures.providerDescriptor(
                "deepseek",
                "DeepSeek",
                false,
                List.of(LlmFixtures.capability("deepseek", "deepseek-v4-pro"))
            ),
            LlmFixtures.providerDescriptor(
                "openai-responses",
                "OpenAI Responses",
                true,
                List.of()
            )
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LlmController(llmPort, currentAccount)).build();

        mvc.perform(get("/api/llm/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].providerKey").value("deepseek"))
            .andExpect(jsonPath("$.data[0].customEndpoint").value(false))
            .andExpect(jsonPath("$.data[0].models[0].model").value("deepseek-v4-pro"))
            .andExpect(jsonPath("$.data[0].models[0].supportedReasoningLevels[1]").value("LOW"))
            .andExpect(jsonPath("$.data[0].models[0].supportedReasoningLevels[2]").value("HIGH"))
            .andExpect(jsonPath("$.data[0].models[0].supportedReasoningLevels[3]").value("MAX"))
            .andExpect(jsonPath("$.data[0].enabled").doesNotExist())
            .andExpect(jsonPath("$.data[0].availableModels").doesNotExist())
            .andExpect(jsonPath("$.data[1].providerKey").value("openai-responses"))
            .andExpect(jsonPath("$.data[1].customEndpoint").value(true))
            .andExpect(jsonPath("$.data[1].models").isEmpty());
    }

    @Test
    void selectedCustomModelCapabilityEndpointReturnsBackendConfirmedReasoningLevels() throws Exception {
        LlmPort llmPort = mock(LlmPort.class);
        var currentAccount = AccountFixtures.current(7L);
        when(llmPort.discoverCustomModelCapability(eq(7L), any()))
            .thenReturn(LlmFixtures.customCapability(
                "openai-chat-completions",
                "account-model",
                LlmFixtures.allReasoningLevels()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LlmController(llmPort, currentAccount)).build();

        mvc.perform(post("/api/llm/config/discover-capabilities")
                .contentType("application/json")
                .content("""
                    {
                      "provider":"openai-chat-completions",
                      "baseUrl":"https://example.com/v1",
                      "apiKey":"sk-test",
                      "model":"account-model"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provider").value("openai-chat-completions"))
            .andExpect(jsonPath("$.data.model").value("account-model"))
            .andExpect(jsonPath("$.data.supportedReasoningLevels[0]").value("AUTO"))
            .andExpect(jsonPath("$.data.supportedReasoningLevels[4]").value("XHIGH"))
            .andExpect(jsonPath("$.data.supportedReasoningLevels[5]").value("MAX"));
    }

    @Test
    void configurationWriteUsesCanonicalMaxOutputTokensField() throws Exception {
        LlmPort llmPort = mock(LlmPort.class);
        var currentAccount = AccountFixtures.current(7L);
        when(llmPort.saveConfiguration(eq(7L), any()))
            .thenReturn(LlmFixtures.configView(
                "deepseek", "deepseek-v4-pro", null, false, null, "HIGH", 8192, List.of(),
                LlmFixtures.capability("deepseek", "deepseek-v4-pro")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LlmController(llmPort, currentAccount)).build();

        mvc.perform(put("/api/llm/config")
                .contentType("application/json")
                .content("""
                    {
                      "provider":"deepseek",
                      "model":"deepseek-v4-pro",
                      "reasoningLevel":"HIGH",
                      "maxOutputTokens":8192,
                      "fallbackModels":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maxOutputTokens").value(8192));

        int tokens = LlmFixtures.captureSavedMaxOutputTokens(llmPort, 7L);
        assertThat(tokens).isEqualTo(8192);
    }
}
