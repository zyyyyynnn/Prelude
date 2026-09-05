package com.prelude.llm;

import com.prelude.identity.api.CurrentAccount;
import com.prelude.llm.api.LlmController;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ProviderDescriptorView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        CurrentAccount currentAccount = mock(CurrentAccount.class);
        when(currentAccount.requireId()).thenReturn(7L);
        when(llmPort.listModels(7L)).thenReturn(List.of(
            new ProviderDescriptorView(
                "deepseek",
                "DeepSeek",
                false,
                List.of(new ModelCapabilityResponse(
                    "deepseek",
                    "deepseek-v4-pro",
                    true,
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    false,
                    false,
                    List.of(ModelCapabilityResponse.ReasoningLevel.AUTO,
                        ModelCapabilityResponse.ReasoningLevel.LOW,
                        ModelCapabilityResponse.ReasoningLevel.HIGH,
                        ModelCapabilityResponse.ReasoningLevel.MAX)
                ))
            ),
            new ProviderDescriptorView(
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
        CurrentAccount currentAccount = mock(CurrentAccount.class);
        when(currentAccount.requireId()).thenReturn(7L);
        when(llmPort.discoverCustomModelCapability(
            org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ModelCapabilityCatalog().customCapability(
                "openai-chat-completions",
                "account-model",
                List.of(ModelCapabilityResponse.ReasoningLevel.AUTO,
                    ModelCapabilityResponse.ReasoningLevel.LOW,
                    ModelCapabilityResponse.ReasoningLevel.MEDIUM,
                    ModelCapabilityResponse.ReasoningLevel.HIGH,
                    ModelCapabilityResponse.ReasoningLevel.XHIGH,
                    ModelCapabilityResponse.ReasoningLevel.MAX)));
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
        CurrentAccount currentAccount = mock(CurrentAccount.class);
        when(currentAccount.requireId()).thenReturn(7L);
        when(llmPort.saveConfiguration(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new com.prelude.llm.api.ModelConfigurationView(
                "deepseek", "deepseek-v4-pro", null, false, null, "HIGH", 8192, List.of(),
                new ModelCapabilityCatalog().capability("deepseek", "deepseek-v4-pro")));
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

        org.mockito.ArgumentCaptor<com.prelude.llm.api.SaveConfigurationCommand> command =
            org.mockito.ArgumentCaptor.forClass(com.prelude.llm.api.SaveConfigurationCommand.class);
        verify(llmPort).saveConfiguration(org.mockito.ArgumentMatchers.eq(7L), command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().maxOutputTokens()).isEqualTo(8192);
    }
}
