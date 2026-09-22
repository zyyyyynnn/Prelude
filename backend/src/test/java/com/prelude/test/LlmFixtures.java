package com.prelude.test;

import com.prelude.llm.ModelCapabilityCatalog;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.LlmUsageRecorded;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.ProviderDescriptorView;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.llm.infrastructure.persistence.ModelExecutionSnapshot;
import com.prelude.llm.infrastructure.persistence.ModelExecutionSnapshotMapper;
import com.prelude.llm.application.port.ModelProfileStore.ProfileRow;
import com.prelude.llm.infrastructure.persistence.ModelProfile;
import com.prelude.llm.infrastructure.persistence.ModelProfileMapper;
import com.prelude.llm.infrastructure.persistence.ProviderCredential;
import com.prelude.llm.infrastructure.persistence.ProviderCredentialMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LlmFixtures {

    private static final ModelCapabilityCatalog CATALOG = new ModelCapabilityCatalog();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LlmFixtures() {
    }

    public static LlmPort mockPort() {
        return Mockito.mock(LlmPort.class);
    }

    public static LlmPort mockResponseModePort() {
        LlmPort port = Mockito.mock(LlmPort.class);
        Mockito.when(port.complete(Mockito.any())).thenAnswer(invocation -> {
            LlmPort.ModelExecutionRequest request = invocation.getArgument(0);
            return new LlmPort.CompletionResult(
                request.responseMode() == LlmPort.ResponseMode.JSON_ARRAY ? "[]" : "{}",
                null
            );
        });
        return port;
    }

    public static List<LlmPort.ResponseMode> captureResponseModes(LlmPort port, int expectedCalls) {
        ArgumentCaptor<LlmPort.ModelExecutionRequest> requests =
            ArgumentCaptor.forClass(LlmPort.ModelExecutionRequest.class);
        Mockito.verify(port, Mockito.times(expectedCalls)).complete(requests.capture());
        return requests.getAllValues().stream()
            .map(LlmPort.ModelExecutionRequest::responseMode)
            .toList();
    }

    public static LlmPort.ResponseMode responseModePlainText() {
        return LlmPort.ResponseMode.PLAIN_TEXT;
    }

    public static LlmPort.ResponseMode responseModeJsonObject() {
        return LlmPort.ResponseMode.JSON_OBJECT;
    }

    public static LlmPort.ResponseMode responseModeJsonArray() {
        return LlmPort.ResponseMode.JSON_ARRAY;
    }

    public static ModelExecutionSnapshot snapshot(String provider, String model, String reasoningLevel, String customEndpointUrl) {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(reasoningLevel);
        snapshot.setCustomEndpointUrl(customEndpointUrl);
        return snapshot;
    }

    public static ModelExecutionSnapshot snapshotWithDefaults(
        Long id, Long accountId, Long profileId, String provider, String model, String reasoningLevel, int maxTokens) {
        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setId(id);
        snapshot.setAccountId(accountId);
        snapshot.setProfileId(profileId);
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setReasoningLevel(reasoningLevel);
        snapshot.setEffectiveParametersJson("{\"maxOutputTokens\":" + maxTokens + "}");
        try {
            snapshot.setModelCapabilityJson(OBJECT_MAPPER.writeValueAsString(CATALOG.capability(provider, model)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        snapshot.setFallbackCapabilitiesJson("[]");
        return snapshot;
    }

    public static SaveConfigurationCommand saveConfigurationCommand(
        String provider, String model, String customEndpointUrl,
        String apiKey, String reasoningLevel, Integer maxTokens, List<String> fallbackModels) {
        return new SaveConfigurationCommand(provider, model, customEndpointUrl, apiKey, reasoningLevel, maxTokens, fallbackModels);
    }

    public static LlmPort.FreezeSnapshotCommand freezeSnapshotCommand(long accountId, String reasoningLevel, String modelOverride) {
        return new LlmPort.FreezeSnapshotCommand(accountId, reasoningLevel, modelOverride);
    }

    public static LlmPort.DiscoverModelsCommand discoverModelsCommand(String provider, String baseUrl, String apiKey) {
        return new LlmPort.DiscoverModelsCommand(provider, baseUrl, apiKey);
    }

    public static LlmPort.ModelExecutionRequest executionRequest(
        long snapshotId, String purpose, String promptId, LlmPort.ResponseMode responseMode, String userMessage) {
        return new LlmPort.ModelExecutionRequest(
            snapshotId, purpose, promptId, responseMode,
            List.of(new LlmPort.Message("user", userMessage)), List.of(), List.of());
    }

    public static LlmPort.ModelExecutionRequest executionRequest(
        long snapshotId, String purpose, String promptId, LlmPort.ResponseMode responseMode,
        List<LlmPort.Message> messages, List<LlmPort.Attachment> attachments, List<LlmPort.ToolBinding> tools) {
        return new LlmPort.ModelExecutionRequest(
            snapshotId, purpose, promptId, responseMode, messages, attachments, tools);
    }

    public static ModelCapabilityResponse customCapability(String provider, String model, List<ModelCapabilityResponse.ReasoningLevel> levels) {
        return customCapability(provider, model, levels, false, false);
    }

    public static ModelCapabilityResponse customCapability(
        String provider, String model, List<ModelCapabilityResponse.ReasoningLevel> levels, boolean structuredOutput, boolean vision) {
        List<ModelCapabilityResponse.ReasoningLevel> effectiveLevels = (levels == null || levels.isEmpty())
            ? List.of(ModelCapabilityResponse.ReasoningLevel.AUTO)
            : List.copyOf(levels);
        return new ModelCapabilityResponse(
            provider, model, effectiveLevels.size() > 1, structuredOutput, false, true, vision,
            false, false, false, false, effectiveLevels);
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningAuto() {
        return ModelCapabilityResponse.ReasoningLevel.AUTO;
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningLow() {
        return ModelCapabilityResponse.ReasoningLevel.LOW;
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningMedium() {
        return ModelCapabilityResponse.ReasoningLevel.MEDIUM;
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningHigh() {
        return ModelCapabilityResponse.ReasoningLevel.HIGH;
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningXHigh() {
        return ModelCapabilityResponse.ReasoningLevel.XHIGH;
    }

    public static ModelCapabilityResponse.ReasoningLevel reasoningMax() {
        return ModelCapabilityResponse.ReasoningLevel.MAX;
    }

    public static List<ModelCapabilityResponse.ReasoningLevel> allReasoningLevels() {
        return List.of(
            ModelCapabilityResponse.ReasoningLevel.AUTO,
            ModelCapabilityResponse.ReasoningLevel.LOW,
            ModelCapabilityResponse.ReasoningLevel.MEDIUM,
            ModelCapabilityResponse.ReasoningLevel.HIGH,
            ModelCapabilityResponse.ReasoningLevel.XHIGH,
            ModelCapabilityResponse.ReasoningLevel.MAX);
    }

    public static String capabilityJson(String provider, String model) {
        try {
            return OBJECT_MAPPER.writeValueAsString(CATALOG.capability(provider, model));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String customCapabilityJson(
        String provider, String model, List<ModelCapabilityResponse.ReasoningLevel> levels, boolean structuredOutput, boolean vision) {
        try {
            return OBJECT_MAPPER.writeValueAsString(customCapability(provider, model, levels, structuredOutput, vision));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ModelCapabilityResponse asCapabilityResponse(ModelCapabilityResponse capability) {
        return capability;
    }

    public static Class<ModelCapabilityResponse> modelCapabilityResponseClass() {
        return ModelCapabilityResponse.class;
    }

    public static ModelProfile profile(Long id, Long accountId, String provider, String model, Long credentialId, String customEndpointUrl, String reasoningLevel) {
        ModelProfile profile = new ModelProfile();
        profile.setId(id);
        profile.setAccountId(accountId);
        profile.setProvider(provider);
        profile.setModel(model);
        profile.setCredentialId(credentialId);
        profile.setCustomEndpointUrl(customEndpointUrl);
        profile.setReasoningLevel(reasoningLevel);
        profile.setFallbackCapabilitiesJson("[]");
        return profile;
    }

    public static String getModelCapabilityJson(ModelProfile profile) {
        return profile.getModelCapabilityJson();
    }

    public static String getFallbackCapabilitiesJson(ModelProfile profile) {
        return profile.getFallbackCapabilitiesJson();
    }

    public static ProviderCredential credential(Long id, Long accountId, String provider, String scopeKey, String encryptedKey) {
        ProviderCredential cred = new ProviderCredential();
        cred.setId(id);
        cred.setAccountId(accountId);
        cred.setProvider(provider);
        cred.setScopeKey(scopeKey);
        cred.setApiKeyEncrypted(encryptedKey);
        return cred;
    }

    public static ProviderCredentialMapper mockCredentialMapper() {
        return Mockito.mock(ProviderCredentialMapper.class);
    }


    /** A profile store backed by a mock mapper, for the same reason. */
    public static com.prelude.llm.application.port.ModelProfileStore profileStoreOver(
        ModelProfileMapper profileMapper) {
        return new com.prelude.llm.application.port.ModelProfileStore() {
            @Override
            public java.util.Optional<ProfileRow> findActiveByAccount(Long accountId) {
                ModelProfile profile = profileMapper.selectOne(org.mockito.ArgumentMatchers.any());
                return profile == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(toRow(profile));
            }

            @Override
            public java.util.Optional<ProfileRow> findActiveForUpdate(Long accountId) {
                return findActiveByAccount(accountId);
            }

            @Override
            public ProfileRow insert(ProfileRow row) {
                ModelProfile profile = new ModelProfile();
                apply(profile, row);
                profileMapper.insert(profile);
                return toRow(profile);
            }

            @Override
            public void update(ProfileRow row) {
                ModelProfile profile = new ModelProfile();
                apply(profile, row);
                profile.setId(row.id());
                profileMapper.updateById(profile);
            }

            private void apply(ModelProfile profile, ProfileRow row) {
                profile.setAccountId(row.accountId());
                profile.setProvider(row.provider());
                profile.setModel(row.model());
                profile.setCustomEndpointUrl(row.customEndpointUrl());
                profile.setReasoningLevel(row.reasoningLevel());
                profile.setEffectiveParametersJson(row.effectiveParametersJson());
                profile.setModelCapabilityJson(row.modelCapabilityJson());
                profile.setFallbackCapabilitiesJson(row.fallbackCapabilitiesJson());
                profile.setCredentialId(row.credentialId());
            }

            private ProfileRow toRow(ModelProfile profile) {
                return new ProfileRow(
                    profile.getId(), profile.getAccountId(), profile.getProvider(), profile.getModel(),
                    profile.getCustomEndpointUrl(), profile.getReasoningLevel(),
                    profile.getEffectiveParametersJson(), profile.getModelCapabilityJson(),
                    profile.getFallbackCapabilitiesJson(), profile.getCredentialId());
            }
        };
    }

    /**
     * A credential store backed by a mock mapper, so a test can keep stubbing
     * {@code selectById}/{@code insert} on the mapper while the service depends on the port.
     */
    public static com.prelude.llm.application.port.ProviderCredentialStore credentialStoreOver(
        ProviderCredentialMapper credentialMapper) {
        return new com.prelude.llm.application.port.ProviderCredentialStore() {
            @Override
            public String findOwnedEncryptedKey(Long accountId, Long credentialId) {
                if (credentialId == null) {
                    return null;
                }
                ProviderCredential credential = credentialMapper.selectById(credentialId);
                if (credential == null || !accountId.equals(credential.getAccountId())) {
                    throw com.prelude.BusinessException.badRequest("模型凭证不存在或不属于当前账户");
                }
                return credential.getApiKeyEncrypted();
            }

            @Override
            public java.util.Optional<com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow>
                findById(Long credentialId) {
                ProviderCredential credential = credentialMapper.selectById(credentialId);
                return credential == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(toRow(credential));
            }

            @Override
            public com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow insert(
                com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow row) {
                ProviderCredential credential = new ProviderCredential();
                credential.setAccountId(row.accountId());
                credential.setProvider(row.provider());
                credential.setScopeKey(row.scopeKey());
                credential.setApiKeyEncrypted(row.apiKeyEncrypted());
                credentialMapper.insert(credential);
                return toRow(credential);
            }

            private com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow toRow(
                ProviderCredential credential) {
                return new com.prelude.llm.application.port.ProviderCredentialStore.CredentialRow(
                    credential.getId(), credential.getAccountId(), credential.getProvider(),
                    credential.getScopeKey(), credential.getApiKeyEncrypted());
            }
        };
    }

    public static ModelProfileMapper mockProfileMapper() {
        return Mockito.mock(ModelProfileMapper.class);
    }

    /** Reads a stubbed profile row back as the port's projection. */
    public static com.prelude.llm.application.port.ModelProfileStore.ProfileRow profileRowOf(
        ModelProfile profile) {
        return new com.prelude.llm.application.port.ModelProfileStore.ProfileRow(
            profile.getId(), profile.getAccountId(), profile.getProvider(), profile.getModel(),
            profile.getCustomEndpointUrl(), profile.getReasoningLevel(),
            profile.getEffectiveParametersJson(), profile.getModelCapabilityJson(),
            profile.getFallbackCapabilitiesJson(), profile.getCredentialId());
    }

    /** Writes a projection back onto a stubbed profile row, so a later read sees it. */
    public static void applyToProfile(
        ModelProfile profile,
        com.prelude.llm.application.port.ModelProfileStore.ProfileRow row) {
        profile.setId(row.id());
        profile.setAccountId(row.accountId());
        profile.setProvider(row.provider());
        profile.setModel(row.model());
        profile.setCustomEndpointUrl(row.customEndpointUrl());
        profile.setReasoningLevel(row.reasoningLevel());
        profile.setEffectiveParametersJson(row.effectiveParametersJson());
        profile.setModelCapabilityJson(row.modelCapabilityJson());
        profile.setFallbackCapabilitiesJson(row.fallbackCapabilitiesJson());
        profile.setCredentialId(row.credentialId());
    }

    public static void verifyNeverUpdated(ModelProfileMapper profileMapper) {
        Mockito.verify(profileMapper, Mockito.never()).updateById(Mockito.any(ModelProfile.class));
    }

    public static ModelExecutionSnapshotMapper mockSnapshotMapper() {
        return Mockito.mock(ModelExecutionSnapshotMapper.class);
    }

    public static ModelCapabilityResponse capability(String provider, String model) {
        return CATALOG.capability(provider, model);
    }

    public static ModelConfigurationView configView(
        String provider, String model, String customEndpointUrl, boolean hasApiKey,
        String apiKeyMasked, String reasoningLevel, Integer maxTokens,
        List<String> fallbackModels, ModelCapabilityResponse capability) {
        return new ModelConfigurationView(
            provider, model, customEndpointUrl, hasApiKey, apiKeyMasked, reasoningLevel, maxTokens, fallbackModels, capability);
    }

    public static int captureSavedMaxOutputTokens(LlmPort port, long accountId) {
        ArgumentCaptor<SaveConfigurationCommand> cmd = ArgumentCaptor.forClass(SaveConfigurationCommand.class);
        Mockito.verify(port).saveConfiguration(Mockito.eq(accountId), cmd.capture());
        return cmd.getValue().maxOutputTokens();
    }

    public static ProviderDescriptorView providerDescriptor(
        String providerKey, String displayName, boolean customEndpoint, List<ModelCapabilityResponse> models) {
        return new ProviderDescriptorView(providerKey, displayName, customEndpoint, models);
    }

    public static boolean isUsageRecorded(Object event) {
        return event instanceof LlmUsageRecorded;
    }

    public static UsageRecordView asUsageRecord(Object event) {
        if (event instanceof LlmUsageRecorded usage) {
            return new UsageRecordView(usage);
        }
        return null;
    }

    public record UsageRecordView(LlmUsageRecorded event) {
        public long accountId() { return event.accountId(); }
        public long snapshotId() { return event.snapshotId(); }
        public String purpose() { return event.purpose(); }
        public String promptId() { return event.promptId(); }
        public String provider() { return event.provider(); }
        public String model() { return event.model(); }
        public Long inputTokens() { return event.inputTokens(); }
        public Long outputTokens() { return event.outputTokens(); }
        public Long totalTokens() { return event.totalTokens(); }
        public Instant occurredAt() { return event.occurredAt(); }
        public BigDecimal estimatedCost() { return event.estimatedCost(); }
    }

    public static final String SYSTEM_SCOPE = ProviderCredential.SYSTEM_SCOPE;
    public static final String CAPABILITY_VERSION = ModelCapabilityCatalog.CAPABILITY_VERSION;

    public record SnapshotRow(
        long id,
        long accountId,
        long profileId,
        String provider,
        String model,
        String reasoningLevel,
        String effectiveParametersJson,
        String customEndpointUrl,
        Long credentialId,
        String capabilityVersion,
        String modelCapabilityJson
    ) {}

    public record CredentialRow(
        long id,
        long accountId,
        String provider,
        String scopeKey,
        String apiKeyEncrypted
    ) {}

    public static SnapshotRow selectSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc, long snapshotId) {
        return jdbc.queryForObject(
            "SELECT id, account_id, profile_id, provider, model, reasoning_level, " +
            "effective_parameters_json, custom_endpoint_url, credential_id, capability_version, model_capability_json " +
            "FROM model_execution_snapshot WHERE id = ?",
            (rs, rowNum) -> new SnapshotRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getLong("profile_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("reasoning_level"),
                rs.getString("effective_parameters_json"),
                rs.getString("custom_endpoint_url"),
                rs.getObject("credential_id") != null ? rs.getLong("credential_id") : null,
                rs.getString("capability_version"),
                rs.getString("model_capability_json")
            ),
            snapshotId
        );
    }

    public static CredentialRow selectCredential(org.springframework.jdbc.core.JdbcTemplate jdbc, long accountId, String scopeKey) {
        return jdbc.queryForObject(
            "SELECT id, account_id, provider, scope_key, api_key_encrypted " +
            "FROM provider_credential WHERE account_id = ? AND scope_key = ? LIMIT 1",
            (rs, rowNum) -> new CredentialRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("provider"),
                rs.getString("scope_key"),
                rs.getString("api_key_encrypted")
            ),
            accountId, scopeKey
        );
    }

    public static CredentialRow selectFirstCredential(org.springframework.jdbc.core.JdbcTemplate jdbc, long accountId) {
        return jdbc.queryForObject(
            "SELECT id, account_id, provider, scope_key, api_key_encrypted " +
            "FROM provider_credential WHERE account_id = ? LIMIT 1",
            (rs, rowNum) -> new CredentialRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("provider"),
                rs.getString("scope_key"),
                rs.getString("api_key_encrypted")
            ),
            accountId
        );
    }

    public static List<CredentialRow> listCredentials(
        org.springframework.jdbc.core.JdbcTemplate jdbc, long accountId, String provider, String scopeKey) {
        return jdbc.query(
            "SELECT id, account_id, provider, scope_key, api_key_encrypted " +
            "FROM provider_credential WHERE account_id = ? AND provider = ? AND scope_key = ? ORDER BY id ASC",
            (rs, rowNum) -> new CredentialRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("provider"),
                rs.getString("scope_key"),
                rs.getString("api_key_encrypted")
            ),
            accountId, provider, scopeKey
        );
    }

    public static void updateProfileCapability(
        org.springframework.jdbc.core.JdbcTemplate jdbc, long accountId, ModelCapabilityResponse capability) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(capability);
            jdbc.update("UPDATE model_profile SET model_capability_json = ? WHERE account_id = ?", json, accountId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean readCapabilityVision(String capabilityJson) {
        try {
            var node = OBJECT_MAPPER.readTree(capabilityJson);
            return node.has("vision") && node.get("vision").asBoolean();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long ensureAccountProfile(
        org.springframework.jdbc.core.JdbcTemplate jdbc, long accountId, String provider,
        String model, String reasoningLevel, String customEndpointUrl) {
        Long credentialId = null;
        String capabilityJson = null;
        if (customEndpointUrl != null) {
            jdbc.update(
                "INSERT INTO provider_credential (account_id, provider, scope_key, api_key_encrypted, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'encoded', NOW(), NOW())",
                accountId, provider, customEndpointUrl);
            credentialId = jdbc.queryForObject(
                "SELECT id FROM provider_credential WHERE account_id = ? AND scope_key = ? LIMIT 1",
                Long.class, accountId, customEndpointUrl);
            try {
                capabilityJson = OBJECT_MAPPER.writeValueAsString(
                    customCapability(provider, model, List.of(reasoningAuto())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        jdbc.update(
            "INSERT INTO model_profile (account_id, provider, model, reasoning_level, " +
            "effective_parameters_json, fallback_capabilities_json, custom_endpoint_url, credential_id, model_capability_json, updated_at) " +
            "VALUES (?, ?, ?, ?, '{\"maxOutputTokens\":4096}', '[]', ?, ?, ?, NOW())",
            accountId, provider, model, reasoningLevel, customEndpointUrl, credentialId, capabilityJson);
        return accountId;
    }
}