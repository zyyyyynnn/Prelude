package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.infrastructure.persistence.ModelProfileEntity;
import com.prelude.llm.infrastructure.persistence.ModelProfileMapper;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProfileCustomCapabilityTest {

    @Test
    void successfulProbeIsPersistedAndBecomesTheCurrentModelCapabilityTruth() throws Exception {
        Fixture fixture = fixture(LlmFixtures.customCapability(
            "openai-chat-completions", "account-model",
            List.of(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh())));

        var result = fixture.service().saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "HIGH", 4096, List.of()));

        assertThat(result.reasoningLevel()).isEqualTo("HIGH");
        assertThat(result.capability().supportedReasoningLevels())
            .containsExactly(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh());
        assertThat(LlmFixtures.getModelCapabilityJson(fixture.profile())).isNotBlank();
        var stored = new ObjectMapper().readValue(
            LlmFixtures.getModelCapabilityJson(fixture.profile()), LlmFixtures.modelCapabilityResponseClass());
        assertThat(stored.supportedReasoningLevels())
            .containsExactly(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh());
        verify(fixture.capabilityDiscovery()).discover(
            7L, "openai-chat-completions", "https://example.com/v1", "saved-key", "account-model");
    }

    @Test
    void inconclusiveProbeCannotSilentlyKeepANonDefaultReasoningLevel() {
        Fixture fixture = fixture(LlmFixtures.customCapability(
            "openai-chat-completions", "account-model", List.of(LlmFixtures.reasoningAuto())));

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> fixture.service().saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
                "openai-chat-completions", "account-model", "https://example.com/v1",
                null, "HIGH", 4096, List.of())),
            "所选模型不支持该思考深度");

        LlmFixtures.verifyNeverUpdated(fixture.profileMapper());
    }

    @Test
    void customFallbackIsSavedOnlyWhenItsConfirmedCapabilitySupportsTheFrozenReasoningLevel() {
        Fixture fixture = fixture(LlmFixtures.customCapability(
            "openai-chat-completions", "account-model",
            List.of(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh())));
        when(fixture.capabilityDiscovery().discover(
            7L, "openai-chat-completions", "https://example.com/v1", "saved-key", "fallback-model"))
            .thenReturn(LlmFixtures.customCapability(
                "openai-chat-completions", "fallback-model",
                List.of(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh())));

        fixture.service().saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "HIGH", 4096, List.of("fallback-model")));

        assertThat(LlmFixtures.getFallbackCapabilitiesJson(fixture.profile()))
            .contains("fallback-model")
            .contains("HIGH");
    }

    @Test
    void customFallbackIsRejectedWhenItsConfirmedCapabilityCannotPreserveTheSelectedReasoningLevel() {
        Fixture fixture = fixture(LlmFixtures.customCapability(
            "openai-chat-completions", "account-model",
            List.of(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh())));
        when(fixture.capabilityDiscovery().discover(
            7L, "openai-chat-completions", "https://example.com/v1", "saved-key", "fallback-model"))
            .thenReturn(LlmFixtures.customCapability(
                "openai-chat-completions", "fallback-model", List.of(LlmFixtures.reasoningAuto())));

        ExceptionFixtures.assertBusinessException(() -> fixture.service().saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "HIGH", 4096, List.of("fallback-model"))))
            .hasMessageContaining("回退模型不支持所选思考深度");

        verify(fixture.transactionManager(), never()).getTransaction(any());
    }

    @Test
    void providerProbeCompletesBeforeTheDatabaseTransactionStarts() {
        Fixture fixture = fixture(LlmFixtures.customCapability(
            "openai-chat-completions", "account-model", List.of(LlmFixtures.reasoningAuto())));

        fixture.service().saveConfiguration(7L, LlmFixtures.saveConfigurationCommand(
            "openai-chat-completions", "account-model", "https://example.com/v1",
            null, "AUTO", 4096, List.of()));

        var order = inOrder(fixture.capabilityDiscovery(), fixture.transactionManager());
        order.verify(fixture.capabilityDiscovery()).discover(
            7L, "openai-chat-completions", "https://example.com/v1", "saved-key", "account-model");
        order.verify(fixture.transactionManager()).getTransaction(any());
    }

    private Fixture fixture(ModelCapabilityResponse discoveredCapability) {
        var credentialMapper = LlmFixtures.mockCredentialMapper();
        var profileMapper = LlmFixtures.mockProfileMapper();
        ProviderSecretCipher cipher = mock(ProviderSecretCipher.class);
        CustomModelCapabilityDiscovery capabilityDiscovery = mock(CustomModelCapabilityDiscovery.class);
        CustomLlmEgressPolicy egressPolicy = mock(CustomLlmEgressPolicy.class);
        EgressHttpClientFactory clients = mock(EgressHttpClientFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();

        var profile = LlmFixtures.profile(19L, 7L, "openai-chat-completions", "previous-model", 11L, "https://example.com/v1", "AUTO");
        when(profileMapper.selectOne(any())).thenReturn(profile);

        var credential = LlmFixtures.credential(11L, 7L, "openai-chat-completions", "https://example.com/v1", "encrypted");
        when(credentialMapper.selectById(11L)).thenReturn(credential);
        when(cipher.decrypt("encrypted")).thenReturn("saved-key");
        when(cipher.mask("encrypted")).thenReturn("****-key");
        when(capabilityDiscovery.discover(
            eq(7L), eq("openai-chat-completions"), eq("https://example.com/v1"),
            eq("saved-key"), eq("account-model")))
            .thenReturn(LlmFixtures.asCapabilityResponse(discoveredCapability));

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ModelCapabilityJson capabilityJson = new ModelCapabilityJson(objectMapper);

        // The profile row is stubbed, so the store must reflect writes back into that
        // same row; otherwise a save followed by a read would see the pre-save profile.
        com.prelude.llm.application.port.ModelProfileStore profileStore =
            new com.prelude.llm.application.port.ModelProfileStore() {
                @Override
                public java.util.Optional<com.prelude.llm.application.port.ModelProfileStore.ProfileRow>
                    findActiveByAccount(Long accountId) {
                    ModelProfileEntity stored = profileMapper.selectOne(org.mockito.ArgumentMatchers.any());
                    return stored == null
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(LlmFixtures.profileRowOf(stored));
                }

                @Override
                public java.util.Optional<com.prelude.llm.application.port.ModelProfileStore.ProfileRow>
                    findActiveForUpdate(Long accountId) {
                    return findActiveByAccount(accountId);
                }

                @Override
                public com.prelude.llm.application.port.ModelProfileStore.ProfileRow insert(
                    com.prelude.llm.application.port.ModelProfileStore.ProfileRow row) {
                    LlmFixtures.applyToProfile(profile, row);
                    return LlmFixtures.profileRowOf(profile);
                }

                @Override
                public void update(com.prelude.llm.application.port.ModelProfileStore.ProfileRow row) {
                    LlmFixtures.applyToProfile(profile, row);
                }
            };

        ModelProfileService service = new ModelProfileService(
            LlmFixtures.credentialStoreOver(credentialMapper),
            profileStore,
            cipher,
            new ProviderCredentialResolver(LlmFixtures.credentialStoreOver(credentialMapper), cipher),
            new ModelCapabilityCatalog(),
            new ReasoningLevels(),
            capabilityDiscovery,
            new CustomModelCatalogClient(egressPolicy, clients, new ModelCapabilityCatalog(), objectMapper),
            egressPolicy,
            capabilityJson,
            objectMapper,
            transactionTemplate
        );
        return new Fixture(service, profile, profileMapper, capabilityDiscovery, transactionManager);
    }

    private record Fixture(
        ModelProfileService service,
        ModelProfileEntity profile,
        ModelProfileMapper profileMapper,
        CustomModelCapabilityDiscovery capabilityDiscovery,
        PlatformTransactionManager transactionManager
    ) {
    }
}
