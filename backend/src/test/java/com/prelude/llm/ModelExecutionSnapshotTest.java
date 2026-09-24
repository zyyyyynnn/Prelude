package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.test.AccountFixtures;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.LlmFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model execution snapshots against real MySQL: a snapshot freezes provider,
 * model, reasoning, capability version, credential and endpoint; a later
 * profile mutation never rewrites an existing snapshot, and the execution
 * candidates never cross provider/credential boundaries.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest
class ModelExecutionSnapshotTest {

    @Autowired
    private LlmPort llmPort;

    @Autowired
    private ModelProfileService profileService;

    @Autowired
    private ReasoningLevels reasoningLevels;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void frozenSnapshotIgnoresLaterProfileMutation() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        long snapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, "HIGH", null)).snapshotId();
        LlmFixtures.SnapshotRow frozen = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(frozen.model()).isEqualTo("deepseek-v4-pro");
        assertThat(frozen.reasoningLevel()).isEqualTo("HIGH");
        assertThat(frozen.capabilityVersion())
            .isEqualTo(LlmFixtures.CAPABILITY_VERSION);

        // Later profile mutation: the frozen snapshot must not change.
        llmPort.saveConfiguration(accountId, LlmFixtures.saveConfigurationCommand(
            "deepseek", "deepseek-v4-flash", null, null, "AUTO", 8192, List.of()));
        LlmFixtures.SnapshotRow reloaded = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(reloaded.model()).isEqualTo("deepseek-v4-pro");
        assertThat(reloaded.reasoningLevel()).isEqualTo("HIGH");
        assertThat(reloaded.effectiveParametersJson()).contains("\"maxOutputTokens\":4096");
    }

    @Test
    @Transactional
    void requestedModelOverrideFreezesTheComposerSelection() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        long snapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, null, "deepseek-v4-flash")).snapshotId();

        LlmFixtures.SnapshotRow frozen = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(frozen.model()).isEqualTo("deepseek-v4-flash");
    }

    @Test
    @Transactional
    void customEndpointSnapshotFreezesScopeAndCredential() {
        long accountId = createAccountAndProfile(
            "openai-chat-completions", "account-model", "AUTO", "https://example.com/v1");
        LlmFixtures.CredentialRow credential = LlmFixtures.selectCredential(jdbcTemplate, accountId, "https://example.com/v1");

        long snapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, null, null)).snapshotId();

        LlmFixtures.SnapshotRow frozen = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(frozen.provider()).isEqualTo("openai-chat-completions");
        assertThat(frozen.customEndpointUrl()).isEqualTo("https://example.com/v1");
        assertThat(frozen.credentialId()).isEqualTo(credential.id());
        assertThat(frozen.modelCapabilityJson()).contains("account-model");
    }

    @Test
    void reasoningVocabularyIncludesExtraHighAndMaxWithoutGuessingUnknownValues() {
        assertThat(reasoningLevels.parse("xhigh"))
            .isEqualTo(LlmFixtures.reasoningXHigh());
        assertThat(reasoningLevels.parse("max"))
            .isEqualTo(LlmFixtures.reasoningMax());
        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> reasoningLevels.parse("ultra"),
            "思考深度仅支持 AUTO、LOW、MEDIUM、HIGH、XHIGH、MAX");
        assertThat(reasoningLevels.parse("AUTO")).isEqualTo(
            LlmFixtures.reasoningAuto());
    }

    @Test
    @Transactional
    void byokCredentialIsEncryptedAtRestAndMaskedOnRead() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);
        llmPort.saveConfiguration(accountId, LlmFixtures.saveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-live-secret-123456", "AUTO", 4096, List.of()));

        LlmFixtures.CredentialRow stored = LlmFixtures.selectFirstCredential(jdbcTemplate, accountId);
        assertThat(stored.apiKeyEncrypted()).doesNotContain("sk-live-secret");
        assertThat(stored.apiKeyEncrypted()).isNotBlank();

        var view = llmPort.currentConfiguration(accountId);
        assertThat(view.apiKeyMasked()).doesNotContain("sk-live-secret");
        assertThat(view.apiKeyMasked()).startsWith("****");
    }

    @Test
    @Transactional
    void rotatingAKeyCreatesANewCredentialAndKeepsTheFrozenSnapshotOnTheOldSecret() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        llmPort.saveConfiguration(accountId, LlmFixtures.saveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-immutable-A", "AUTO", 4096, List.of()));
        long snapshotAId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, null, null)).snapshotId();
        LlmFixtures.SnapshotRow snapshotA = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotAId);

        llmPort.saveConfiguration(accountId, LlmFixtures.saveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-immutable-B", "AUTO", 4096, List.of()));
        long snapshotBId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, null, null)).snapshotId();
        LlmFixtures.SnapshotRow snapshotB = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotBId);

        assertThat(snapshotA.credentialId()).isNotEqualTo(snapshotB.credentialId());
        assertThat(profileService.resolveApiKey(accountId, snapshotA.credentialId())).isEqualTo("sk-immutable-A");
        assertThat(profileService.resolveApiKey(accountId, snapshotB.credentialId())).isEqualTo("sk-immutable-B");

        List<LlmFixtures.CredentialRow> credentials = LlmFixtures.listCredentials(
            jdbcTemplate, accountId, "deepseek", LlmFixtures.SYSTEM_SCOPE);
        assertThat(credentials).hasSize(2);
        assertThat(credentials).allSatisfy(stored -> {
            assertThat(stored.apiKeyEncrypted()).doesNotContain("sk-immutable-A");
            assertThat(stored.apiKeyEncrypted()).doesNotContain("sk-immutable-B");
        });
    }

    @Test
    @Transactional
    void unsupportedReasoningIsRejectedInsteadOfSilentlyDowngraded() {
        long accountId = createAccountAndProfile(
            "openai-chat-completions", "custom-model", "AUTO", "https://example.com/v1");

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> llmPort.freezeSnapshot(
                LlmFixtures.freezeSnapshotCommand(accountId, "HIGH", null)),
            "所选模型不支持该思考深度");
    }

    @Test
    @Transactional
    void confirmedCustomReasoningCapabilityIsUsedWhenFreezingTheRun() {
        long accountId = createAccountAndProfile(
            "openai-chat-completions", "custom-model", "AUTO", "https://example.com/v1");
        LlmFixtures.updateProfileCapability(jdbcTemplate, accountId,
            LlmFixtures.customCapability("openai-chat-completions", "custom-model",
                List.of(LlmFixtures.reasoningAuto(), LlmFixtures.reasoningHigh())));

        long snapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, "HIGH", null)).snapshotId();

        LlmFixtures.SnapshotRow frozen = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(frozen.reasoningLevel()).isEqualTo("HIGH");
        assertThat(frozen.modelCapabilityJson()).contains("HIGH");

        LlmFixtures.updateProfileCapability(jdbcTemplate, accountId,
            LlmFixtures.customCapability("openai-chat-completions", "custom-model",
                List.of(LlmFixtures.reasoningAuto())));

        LlmFixtures.SnapshotRow reloaded = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(reloaded.modelCapabilityJson()).contains("HIGH");
        long nextSnapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, "AUTO", null)).snapshotId();
        assertThat(LlmFixtures.selectSnapshot(jdbcTemplate, nextSnapshotId).modelCapabilityJson())
            .doesNotContain("HIGH");
    }

    @Test
    @Transactional
    void frozenAnthropicVisionCapabilityIgnoresLaterProfileCapabilityMutation() {
        long accountId = createAccountAndProfile(
            "anthropic-messages", "account-model", "AUTO", "https://example.com");
        LlmFixtures.updateProfileCapability(jdbcTemplate, accountId,
            LlmFixtures.customCapability("anthropic-messages", "account-model",
                List.of(LlmFixtures.reasoningAuto()), false, true));

        long snapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, "AUTO", null)).snapshotId();
        LlmFixtures.SnapshotRow frozen = LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId);
        assertThat(LlmFixtures.readCapabilityVision(frozen.modelCapabilityJson())).isTrue();

        LlmFixtures.updateProfileCapability(jdbcTemplate, accountId,
            LlmFixtures.customCapability("anthropic-messages", "account-model",
                List.of(LlmFixtures.reasoningAuto())));

        assertThat(LlmFixtures.readCapabilityVision(LlmFixtures.selectSnapshot(jdbcTemplate, snapshotId).modelCapabilityJson()))
            .isTrue();
        long nextSnapshotId = llmPort.freezeSnapshot(
            LlmFixtures.freezeSnapshotCommand(accountId, "AUTO", null)).snapshotId();
        assertThat(LlmFixtures.readCapabilityVision(LlmFixtures.selectSnapshot(jdbcTemplate, nextSnapshotId).modelCapabilityJson()))
            .isFalse();
    }

    @Test
    @Transactional
    void unknownBuiltInModelIsRejectedInsteadOfInheritingProviderCapabilities() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> llmPort.saveConfiguration(accountId, LlmFixtures.saveConfigurationCommand(
                "deepseek", "deepseek-unknown", null, null, "AUTO", 4096, List.of())),
            "当前接入方式不支持该模型");
    }

    private long createAccountAndProfile(String provider, String model,
                                         String reasoningLevel, String customEndpointUrl) {
        long accountId = AccountFixtures.create(jdbcTemplate, "llm-" + provider);
        return LlmFixtures.ensureAccountProfile(jdbcTemplate, accountId, provider, model, reasoningLevel, customEndpointUrl);
    }
}
