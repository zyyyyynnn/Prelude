package com.interview.platform.llm;

import com.interview.platform.llm.UserLlmConfigServiceImpl;

import com.interview.platform.llm.api.LlmConfigTestRequest;
import com.interview.platform.llm.api.UserLlmConfigResponse;
import com.interview.platform.llm.api.UserLlmConfigRequest;
import com.interview.identity.domain.User;
import com.interview.platform.llm.LlmRouter;
import com.interview.platform.llm.LlmSelection;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.platform.llm.LlmFixturePort;
import com.interview.platform.llm.LlmModelDiscoveryService;
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
    private LlmFixturePort devFixtureService;

    @Mock
    private LlmModelDiscoveryService llmModelDiscoveryService;

    @InjectMocks
    private UserLlmConfigServiceImpl service;

    @AfterEach
    void tearDown() {
        com.interview.shared.web.UserContext.remove();
    }

    @Test
    void returnsMaskedCurrentUserConfig() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-chat-completions");
        user.setLlmModel("gpt-4o");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("cipher-text");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection())
            .thenReturn(new LlmSelection("openai-chat-completions", "gpt-4o"));
        when(aesGcmEncryptor.mask("cipher-text")).thenReturn("****1234");

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        UserLlmConfigResponse response = service.getCurrentUserConfig();

        assertThat(response.providerKey()).isEqualTo("openai-chat-completions");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(response.hasApiKey()).isTrue();
        assertThat(response.apiKeyMasked()).isEqualTo("****1234");
        verify(aesGcmEncryptor).mask("cipher-text");
    }

    @Test
    void customProviderRequiresBaseUrlWhenSaving() {
        User user = new User();
        user.setId(7L);

        when(userMapper.selectById(7L)).thenReturn(user);

        com.interview.shared.web.UserContext.setCurrentUserId(7L);

        assertThatThrownBy(() -> service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-responses",
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
    void customProviderEncryptsKeyAndSavesNormalizedBaseUrl() {
        User user = new User();
        user.setId(7L);

        when(userMapper.selectById(7L)).thenReturn(user);
        when(aesGcmEncryptor.encrypt("sk-test")).thenReturn("cipher-text");
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection())
            .thenReturn(new LlmSelection("openai-chat-completions", "model-a"));

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-chat-completions",
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
        // 旧配置为用户 BYOK；切换到内置 provider 且不提供新 Key 时必须清空旧 Key。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-chat-completions");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("deepseek", "deepseek-v4-pro"));

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
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
        // 自定义接口的 baseUrl 变化且不提供新 Key 时清空旧 Key。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-chat-completions");
        user.setLlmBaseUrl("https://a.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection())
            .thenReturn(new LlmSelection("openai-chat-completions", "model-a"));

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-chat-completions", "https://b.com/v1", "model-a", null, null, null
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
        user.setLlmProvider("openai-chat-completions");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(llmRouter.resolveCurrentUserSelection())
            .thenReturn(new LlmSelection("openai-chat-completions", "model-a"));

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-chat-completions", "https://example.com/v1", "model-a", null, null, null
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLlmApiKeyEncrypted()).isEqualTo("old-cipher");
    }

    @Test
    void devFixtureClearSentinelClearsStoredApiKeyBeforePlaceholderHandling() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-chat-completions");
        user.setLlmBaseUrl("https://example.com/v1");
        user.setLlmApiKeyEncrypted("fixture-placeholder");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(true);
        when(llmRouter.resolveCurrentUserSelection())
            .thenReturn(new LlmSelection("openai-chat-completions", "model-a"));

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        service.updateCurrentUserConfig(new UserLlmConfigRequest(
            "openai-chat-completions", "https://example.com/v1", "model-a", "__CLEAR__", null, null
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

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        var response = service.testConfig(null);

        assertThat(response.ok()).isTrue();
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void draftTestRejectsCustomScopeChangeWithoutNewKey() {
        // 已保存自定义接口的 baseUrl A；草稿改为 B 且无新 Key时明确报错。
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("openai-responses");
        user.setLlmBaseUrl("https://a.com/v1");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        com.interview.shared.web.UserContext.setCurrentUserId(7L);

        assertThatThrownBy(() -> service.testConfig(new LlmConfigTestRequest(
            "openai-responses", "https://b.com/v1", "model-a", null, null, null
        )))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("重新填写 API Key");
    }

    @Test
    void draftTestUsesSavedBaseUrlWhenCustomBaseUrlBlank() {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider("anthropic-messages");
        user.setLlmBaseUrl("https://saved.example/v1");
        user.setLlmModel("model-a");
        user.setLlmApiKeyEncrypted("old-cipher");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(devFixtureService.isEnabled()).thenReturn(false);
        when(aesGcmEncryptor.decrypt("old-cipher")).thenReturn("sk-saved");
        when(llmRouter.chatWithExplicit(
            eq("anthropic-messages"),
            eq("model-a"),
            eq("https://saved.example/v1"),
            eq("sk-saved"),
            any(),
            eq(null),
            eq(null)
        )).thenReturn("OK");

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
        var response = service.testConfig(new LlmConfigTestRequest(
            "anthropic-messages", "", "model-a", null, null, null
        ));

        assertThat(response.ok()).isTrue();
        verify(llmRouter).chatWithExplicit(
            eq("anthropic-messages"),
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

        com.interview.shared.web.UserContext.setCurrentUserId(7L);
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
