package com.interview.service.impl;

import com.interview.dto.LlmConfigTestRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.entity.User;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import com.interview.service.DevFixtureService;
import com.interview.service.LlmModelDiscoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLlmConfigServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private AesGcmEncryptor aesGcmEncryptor;

    @Mock
    private DevFixtureService devFixtureService;

    @Mock
    private LlmModelDiscoveryService llmModelDiscoveryService;

    @InjectMocks
    private UserLlmConfigServiceImpl service;

    @AfterEach
    void tearDown() {
        com.interview.common.UserContext.remove();
    }

    @Test
    void returnsMaskedCurrentUserConfig() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai");
        user.setLlmModel("gpt-4o");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("cipher-text");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai", "gpt-4o"));
        when(aesGcmEncryptor.mask("cipher-text")).thenReturn("****1234");

        com.interview.common.UserContext.setCurrentUserId(7L);
        UserLlmConfigResponse response = service.getCurrentUserConfig();

        assertThat(response.providerKey()).isEqualTo("openai");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(response.hasApiKey()).isTrue();
        assertThat(response.apiKeyMasked()).isEqualTo("****1234");
        verify(aesGcmEncryptor).mask("cipher-text");
    }

    @Test
    void openAiCompatibleRequiresBaseUrlWhenSaving() {
        User user = new User();
        user.setId(7L);

        when(userMapper.selectById(7L)).thenReturn(user);

        com.interview.common.UserContext.setCurrentUserId(7L);

        assertThatThrownBy(() -> service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-compatible",
            "",
            "model-a",
            "sk-test",
            null,
            null
        )))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Base URL");
    }

    @Test
    void openAiCompatibleEncryptsKeyAndSavesNormalizedBaseUrl() {
        User user = new User();
        user.setId(7L);

        when(userMapper.selectById(7L)).thenReturn(user);
        when(aesGcmEncryptor.encrypt("sk-test")).thenReturn("cipher-text");
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai-compatible", "model-a"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-compatible",
            "https://example.com/v1/chat/completions",
            "model-a",
            "sk-test",
            null,
            null
        ));

        verify(aesGcmEncryptor).encrypt("sk-test");
        verify(userMapper).updateById(user);
    }

    @Test
    void clearsOldKeyWhenProviderChangesAndNoNewKeyProvided() {
        // 旧配置：openai-compatible + 已保存 Key。新请求切换到内置 provider 且不提供新 Key → 旧 Key 必须清空。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("deepseek", "deepseek-v4-pro"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "deepseek", null, "deepseek-v4-pro", null, null, null
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLlmApiKeyEncrypted()).isNull();
        assertThat(captor.getValue().getLlmProvider()).isEqualTo("deepseek");
    }

    @Test
    void clearsOldKeyWhenBaseUrlChangesAndNoNewKeyProvided() {
        // 旧配置：openai-compatible + baseUrl A + 已保存 Key。新请求 baseUrl 变为 B 且不提供新 Key → 旧 Key 清空。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://a.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai-compatible", "model-a"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-compatible", "https://b.com/v1", "model-a", null, null, null
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLlmApiKeyEncrypted()).isNull();
        assertThat(captor.getValue().getLlmBaseUrl()).isEqualTo("https://b.com/v1");
    }

    @Test
    void keepsOldKeyWhenScopeUnchangedAndNoNewKeyProvided() {
        // provider 与 baseUrl 均未变、不提供新 Key → 保留旧 Key（不覆盖语义）。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai-compatible", "model-a"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-compatible", "https://example.com/v1", "model-a", null, null, null
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLlmApiKeyEncrypted()).isEqualTo("old-cipher");
    }

    @Test
    void devFixtureClearSentinelClearsStoredApiKeyBeforePlaceholderHandling() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("fixture-placeholder");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai-compatible", "model-a"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-compatible", "https://example.com/v1", "model-a", "__CLEAR__", null, null
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLlmApiKeyEncrypted()).isNull();
        verify(devFixtureService, never()).nextStoredApiKey(eq("__CLEAR__"), any());
    }

    @Test
    void testConfigNullDoesNotPersistDraft() {
        // 无 body：回退测试已保存配置，绝不写入用户表。
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("deepseek", "deepseek-v4-pro"));

        com.interview.common.UserContext.setCurrentUserId(7L);
        var response = service.testConfig(null);

        assertThat(response.ok()).isTrue();
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void draftTestRejectsOpenAiCompatibleScopeChangeWithoutNewKey() {
        // 已保存 openai-compatible + baseUrl A；草稿 baseUrl 变 B 且无新 Key → 明确报错。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://a.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        com.interview.common.UserContext.setCurrentUserId(7L);

        assertThatThrownBy(() -> service.testConfig(new LlmConfigTestRequest(
            "openai-compatible", "https://b.com/v1", "model-a", null, null, null
        )))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("重新填写 API Key");
    }

    @Test
    void draftTestUsesSavedBaseUrlWhenOpenAiCompatibleBaseUrlBlank() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-compatible");
        user.setLlmBaseUrl("https://saved.example/v1");
        user.setLlmModel("model-a");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(aesGcmEncryptor.decrypt("old-cipher")).thenReturn("sk-saved");
        when(llmRouter.chatWithExplicit(
            eq("openai-compatible"),
            eq("model-a"),
            eq("https://saved.example/v1"),
            eq("sk-saved"),
            any(),
            eq(null),
            eq(null)
        )).thenReturn("OK");

        com.interview.common.UserContext.setCurrentUserId(7L);
        var response = service.testConfig(new LlmConfigTestRequest(
            "openai-compatible", "", "model-a", null, null, null
        ));

        assertThat(response.ok()).isTrue();
        verify(llmRouter).chatWithExplicit(
            eq("openai-compatible"),
            eq("model-a"),
            eq("https://saved.example/v1"),
            eq("sk-saved"),
            any(),
            eq(null),
            eq(null)
        );
    }

    @Test
    void testConfigPassesMaxTokensAndThinkingDepthToRouter() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("deepseek");
        user.setLlmModel("deepseek-v4-pro");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.chatWithExplicit(
            eq("deepseek"),
            eq("deepseek-v4-pro"),
            eq(null),
            eq(null),
            any(),
            eq(8192),
            any()
        )).thenReturn("OK");

        com.interview.common.UserContext.setCurrentUserId(7L);
        service.testConfig(new LlmConfigTestRequest(
            "deepseek", null, "deepseek-v4-pro", null, 8192, "high"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> extraParamsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(llmRouter).chatWithExplicit(
            eq("deepseek"),
            eq("deepseek-v4-pro"),
            eq(null),
            eq(null),
            any(),
            eq(8192),
            extraParamsCaptor.capture()
        );
        assertThat(extraParamsCaptor.getValue()).containsEntry("thinking_depth", "high");
    }
}
