package com.prelude.llm;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;
import com.prelude.llm.persistence.ProviderCredential;
import com.prelude.llm.persistence.ProviderCredentialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    private ModelProfileMapper profileMapper;

    @Autowired
    private ModelExecutionSnapshotMapper snapshotMapper;

    @Autowired
    private ProviderCredentialMapper credentialMapper;

    @Autowired
    private ReasoningLevels reasoningLevels;

    @Test
    @Transactional
    void frozenSnapshotIgnoresLaterProfileMutation() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        ModelExecutionSnapshotRef ref = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, "HIGH", null));
        ModelExecutionSnapshot frozen = snapshotMapper.selectById(ref.snapshotId());
        assertThat(frozen.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(frozen.getReasoningLevel()).isEqualTo("HIGH");
        assertThat(frozen.getCapabilityVersion())
            .isEqualTo(ModelCapabilityCatalog.CAPABILITY_VERSION);

        // Later profile mutation: the frozen snapshot must not change.
        llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-reasoner", null, null, "AUTO", java.util.List.of()));
        ModelExecutionSnapshot reloaded = snapshotMapper.selectById(ref.snapshotId());
        assertThat(reloaded.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(reloaded.getReasoningLevel()).isEqualTo("HIGH");
    }

    @Test
    @Transactional
    void requestedModelOverrideFreezesTheComposerSelection() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        ModelExecutionSnapshotRef ref = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, "deepseek-v4-flash"));

        ModelExecutionSnapshot frozen = snapshotMapper.selectById(ref.snapshotId());
        assertThat(frozen.getModel()).isEqualTo("deepseek-v4-flash");
    }

    @Test
    @Transactional
    void customEndpointSnapshotFreezesScopeAndCredential() {
        long accountId = createAccountAndProfile(
            "openai-compatible", "gpt-4.1", "AUTO", "https://example.com/v1");
        ProviderCredential credential = credentialMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProviderCredential>()
                .eq(ProviderCredential::getAccountId, accountId)
                .eq(ProviderCredential::getScopeKey, "https://example.com/v1")
                .last("LIMIT 1"));

        ModelExecutionSnapshotRef ref = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null));

        ModelExecutionSnapshot frozen = snapshotMapper.selectById(ref.snapshotId());
        assertThat(frozen.getProvider()).isEqualTo("openai-compatible");
        assertThat(frozen.getCustomEndpointUrl()).isEqualTo("https://example.com/v1");
        assertThat(frozen.getCredentialId()).isEqualTo(credential.getId());
    }

    @Test
    void unsupportedReasoningLevelIsRejectedNotDowngraded() {
        assertThatThrownBy(() -> reasoningLevels.parse("xhigh"))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("思考深度仅支持 AUTO、LOW、MEDIUM、HIGH");
        assertThatThrownBy(() -> reasoningLevels.parse("ultra"))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("思考深度仅支持 AUTO、LOW、MEDIUM、HIGH");
        assertThat(reasoningLevels.parse("AUTO")).isEqualTo(
            com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel.AUTO);
    }

    @Test
    @Transactional
    void byokCredentialIsEncryptedAtRestAndMaskedOnRead() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);
        llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-live-secret-123456", "AUTO", java.util.List.of()));

        ProviderCredential stored = credentialMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProviderCredential>()
                .eq(ProviderCredential::getAccountId, accountId)
                .last("LIMIT 1"));
        assertThat(stored.getApiKeyEncrypted()).doesNotContain("sk-live-secret");
        assertThat(stored.getApiKeyEncrypted()).isNotBlank();

        ModelConfigurationView view = llmPort.currentConfiguration(accountId);
        assertThat(view.apiKeyMasked()).doesNotContain("sk-live-secret");
        assertThat(view.apiKeyMasked()).startsWith("****");
    }

    @Test
    @Transactional
    void rotatingAKeyCreatesANewCredentialAndKeepsTheFrozenSnapshotOnTheOldSecret() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-immutable-A", "AUTO", java.util.List.of()));
        ModelExecutionSnapshotRef snapshotARef = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null));
        ModelExecutionSnapshot snapshotA = snapshotMapper.selectById(snapshotARef.snapshotId());

        llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, "sk-immutable-B", "AUTO", java.util.List.of()));
        ModelExecutionSnapshotRef snapshotBRef = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null));
        ModelExecutionSnapshot snapshotB = snapshotMapper.selectById(snapshotBRef.snapshotId());

        assertThat(snapshotA.getCredentialId()).isNotEqualTo(snapshotB.getCredentialId());
        assertThat(profileService.resolveApiKey(accountId, snapshotA.getCredentialId())).isEqualTo("sk-immutable-A");
        assertThat(profileService.resolveApiKey(accountId, snapshotB.getCredentialId())).isEqualTo("sk-immutable-B");

        java.util.List<ProviderCredential> credentials = credentialMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProviderCredential>()
                .eq(ProviderCredential::getAccountId, accountId)
                .eq(ProviderCredential::getProvider, "deepseek")
                .eq(ProviderCredential::getScopeKey, ProviderCredential.SYSTEM_SCOPE)
                .orderByAsc(ProviderCredential::getId));
        assertThat(credentials).hasSize(2);
        assertThat(credentials).allSatisfy(stored -> {
            assertThat(stored.getApiKeyEncrypted()).doesNotContain("sk-immutable-A");
            assertThat(stored.getApiKeyEncrypted()).doesNotContain("sk-immutable-B");
        });
    }

    @Test
    @Transactional
    void unsupportedReasoningIsRejectedInsteadOfSilentlyDowngraded() {
        long accountId = createAccountAndProfile(
            "openai-compatible", "custom-model", "AUTO", "https://example.com/v1");

        assertThatThrownBy(() -> llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, "HIGH", null)))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("所选模型不支持该思考深度");
    }

    @Test
    @Transactional
    void unknownBuiltInModelIsRejectedInsteadOfInheritingProviderCapabilities() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        assertThatThrownBy(() -> llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-unknown", null, null, "AUTO", java.util.List.of())))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("当前接入方式不支持该模型");
    }

    @Test
    @Transactional
    void fallbackMustSupportTheSameFrozenReasoningLevel() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);

        assertThatThrownBy(() -> llmPort.saveConfiguration(accountId, new SaveConfigurationCommand(
            "deepseek", "deepseek-v4-pro", null, null, "HIGH", java.util.List.of("deepseek-chat"))))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("回退模型不支持所选思考深度");
    }

    @Test
    @Transactional
    void reasoningOverrideCannotFreezeAnIncompatibleFallback() {
        long accountId = createAccountAndProfile("deepseek", "deepseek-v4-pro", "AUTO", null);
        ModelProfile profile = profileMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelProfile>()
                .eq(ModelProfile::getAccountId, accountId)
                .last("LIMIT 1"));
        profile.setFallbackModelsJson("[\"deepseek-chat\"]");
        profileMapper.updateById(profile);

        assertThatThrownBy(() -> llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, "HIGH", null)))
            .isInstanceOf(com.prelude.BusinessException.class)
            .hasMessage("回退模型不支持所选思考深度");
    }

    private long createAccountAndProfile(String provider, String model,
                                         String reasoningLevel, String customEndpointUrl) {
        com.prelude.identity.Account account = new com.prelude.identity.Account();
        account.setUsername("llm-" + provider + "-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);
        ModelProfile profile = new ModelProfile();
        profile.setAccountId(account.getId());
        profile.setProvider(provider);
        profile.setModel(model);
        profile.setReasoningLevel(reasoningLevel);
        profile.setCustomEndpointUrl(customEndpointUrl);
        profile.setFallbackModelsJson("[]");
        if (customEndpointUrl != null) {
            ProviderCredential credential = new ProviderCredential();
            credential.setAccountId(account.getId());
            credential.setProvider(provider);
            credential.setScopeKey(customEndpointUrl);
            credential.setApiKeyEncrypted("encoded");
            credentialMapper.insert(credential);
            profile.setCredentialId(credential.getId());
        }
        profileMapper.insert(profile);
        return account.getId();
    }

    @Autowired
    private com.prelude.identity.AccountMapper accountMapper;
}
