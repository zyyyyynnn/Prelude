package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;
import com.prelude.llm.persistence.ProviderCredential;
import com.prelude.llm.persistence.ProviderCredentialMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProfileCustomCapabilityTest {

    @Test
    void successfulProbeIsPersistedAndBecomesTheCurrentModelCapabilityTruth() throws Exception {
        Fixture fixture = fixture(capability(List.of(ReasoningLevel.AUTO, ReasoningLevel.HIGH)));

        var result = fixture.service().saveConfiguration(7L, new SaveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "HIGH", 4096, List.of()));

        assertThat(result.reasoningLevel()).isEqualTo("HIGH");
        assertThat(result.capability().supportedReasoningLevels())
            .containsExactly(ReasoningLevel.AUTO, ReasoningLevel.HIGH);
        assertThat(fixture.profile().getModelCapabilityJson()).isNotBlank();
        ModelCapabilityResponse stored = new ObjectMapper().readValue(
            fixture.profile().getModelCapabilityJson(), ModelCapabilityResponse.class);
        assertThat(stored.supportedReasoningLevels())
            .containsExactly(ReasoningLevel.AUTO, ReasoningLevel.HIGH);
        verify(fixture.capabilityDiscovery()).discover(
            "openai-chat-completions", "https://example.com/v1", "saved-key", "account-model");
    }

    @Test
    void inconclusiveProbeCannotSilentlyKeepANonDefaultReasoningLevel() {
        Fixture fixture = fixture(capability(List.of(ReasoningLevel.AUTO)));

        assertThatThrownBy(() -> fixture.service().saveConfiguration(7L, new SaveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "HIGH", 4096, List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessage("所选模型不支持该思考深度");

        verify(fixture.profileMapper(), never()).updateById(any(ModelProfile.class));
    }

    private Fixture fixture(ModelCapabilityResponse discoveredCapability) {
        ProviderCredentialMapper credentialMapper = mock(ProviderCredentialMapper.class);
        ModelProfileMapper profileMapper = mock(ModelProfileMapper.class);
        ProviderSecretCipher cipher = mock(ProviderSecretCipher.class);
        CustomModelCapabilityDiscovery capabilityDiscovery = mock(CustomModelCapabilityDiscovery.class);
        CustomLlmEgressPolicy egressPolicy = mock(CustomLlmEgressPolicy.class);
        EgressHttpClientFactory clients = mock(EgressHttpClientFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ModelProfile profile = new ModelProfile();
        profile.setId(19L);
        profile.setAccountId(7L);
        profile.setProvider("openai-chat-completions");
        profile.setModel("previous-model");
        profile.setCredentialId(11L);
        profile.setCustomEndpointUrl("https://example.com/v1");
        profile.setReasoningLevel("AUTO");
        profile.setFallbackModelsJson("[]");
        when(profileMapper.selectOne(any())).thenReturn(profile);

        ProviderCredential credential = new ProviderCredential();
        credential.setId(11L);
        credential.setAccountId(7L);
        credential.setProvider("openai-chat-completions");
        credential.setScopeKey("https://example.com/v1");
        credential.setApiKeyEncrypted("encrypted");
        when(credentialMapper.selectById(11L)).thenReturn(credential);
        when(cipher.decrypt("encrypted")).thenReturn("saved-key");
        when(cipher.mask("encrypted")).thenReturn("****-key");
        when(capabilityDiscovery.discover(
            eq("openai-chat-completions"), eq("https://example.com/v1"),
            eq("saved-key"), eq("account-model")))
            .thenReturn(discoveredCapability);

        ModelProfileService service = new ModelProfileService(
            credentialMapper,
            profileMapper,
            cipher,
            new ModelCapabilityCatalog(),
            new ReasoningLevels(),
            capabilityDiscovery,
            egressPolicy,
            clients,
            objectMapper
        );
        return new Fixture(service, profile, profileMapper, capabilityDiscovery);
    }

    private ModelCapabilityResponse capability(List<ReasoningLevel> levels) {
        return new ModelCapabilityCatalog().customCapability(
            "openai-chat-completions", "account-model", levels);
    }

    private record Fixture(
        ModelProfileService service,
        ModelProfile profile,
        ModelProfileMapper profileMapper,
        CustomModelCapabilityDiscovery capabilityDiscovery
    ) {
    }
}
