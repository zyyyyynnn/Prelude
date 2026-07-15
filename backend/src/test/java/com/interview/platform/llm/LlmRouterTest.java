package com.interview.platform.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.web.UserContext;
import com.interview.platform.llm.persistence.LlmProviderConfig;
import com.interview.identity.domain.User;
import com.interview.platform.llm.persistence.LlmProviderConfigMapper;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.platform.realtime.RealtimePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRouterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final LlmProviderConfigMapper llmProviderConfigMapper = mock(LlmProviderConfigMapper.class);
    private final AesGcmEncryptor aesGcmEncryptor = mock(AesGcmEncryptor.class);
    private final RealtimePort sseEmitterRegistry = mock(RealtimePort.class);
    private final CapturingProvider provider = new CapturingProvider();
    private final LlmRouter router = new LlmRouter(
        userMapper,
        llmProviderConfigMapper,
        aesGcmEncryptor,
        new ObjectMapper(),
        List.of(provider),
        sseEmitterRegistry
    );

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void chatWithSnapshotMergesCallerExtraParamsWithUserParams() {
        User user = new User();
        user.setId(9L);
        user.setLlmThinkingDepth("deep");

        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("test");
        config.setBaseUrl("https://example.test/chat");
        config.setAvailableModels("[\"model-a\"]");
        config.setEnabled(1);

        when(userMapper.selectById(9L)).thenReturn(user);
        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        UserContext.setCurrentUserId(9L);
        String result = router.chatWithSnapshot(
            "test",
            "model-a",
            List.of(Map.of("role", "user", "content", "hello")),
            Map.of("response_format", Map.of("type", "json_object"))
        );

        assertThat(result).isEqualTo("ok");
        assertThat(provider.lastInvocation.extraParams())
            .containsEntry("thinking_depth", "deep")
            .containsKey("response_format");
    }

    @Test
    void customChatCompletionsProviderUsesUserBaseUrl() {
        CapturingProvider customProvider = new CapturingProvider(
            "openai-chat-completions", "OpenAI Chat Completions", "");
        LlmRouter customRouter = new LlmRouter(
            userMapper,
            llmProviderConfigMapper,
            aesGcmEncryptor,
            new ObjectMapper(),
            List.of(customProvider),
            sseEmitterRegistry
        );

        User user = new User();
        user.setId(9L);
        user.setLlmBaseUrl("https://example.com/v1/");
        user.setLlmApiKeyEncrypted("cipher");

        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("openai-chat-completions");
        config.setBaseUrl("");
        config.setAvailableModels("[]");
        config.setEnabled(1);

        when(userMapper.selectById(9L)).thenReturn(user);
        when(aesGcmEncryptor.decrypt("cipher")).thenReturn("sk-user");
        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        UserContext.setCurrentUserId(9L);
        String result = customRouter.chatWithSnapshot(
            "openai-chat-completions",
            "model-a",
            List.of(Map.of("role", "user", "content", "hello"))
        );

        assertThat(result).isEqualTo("ok");
        assertThat(customProvider.lastInvocation.baseUrl())
            .isEqualTo("https://example.com/v1/chat/completions");
        assertThat(customProvider.lastInvocation.model()).isEqualTo("model-a");
    }

    @Test
    void customProviderFailureDoesNotFallback() {
        // 用户 BYOK 主调用失败时不得 fallback 到系统通道。
        FailingProvider failingProvider = new FailingProvider(
            "openai-chat-completions", "OpenAI Chat Completions", "",
            new com.interview.shared.api.LlmServerException("upstream 500"));
        CapturingProvider fallbackProvider = new CapturingProvider("deepseek", "DeepSeek", "deepseek-v4-pro");
        LlmRouter customRouter = new LlmRouter(
            userMapper,
            llmProviderConfigMapper,
            aesGcmEncryptor,
            new ObjectMapper(),
            List.of(failingProvider, fallbackProvider),
            sseEmitterRegistry
        );

        User user = new User();
        user.setId(9L);
        user.setLlmBaseUrl("https://example.com/v1/");
        user.setLlmApiKeyEncrypted("cipher");

        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("openai-chat-completions");
        config.setBaseUrl("");
        config.setAvailableModels("[]");
        config.setEnabled(1);

        when(userMapper.selectById(9L)).thenReturn(user);
        when(aesGcmEncryptor.decrypt("cipher")).thenReturn("sk-user");
        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        UserContext.setCurrentUserId(9L);
        assertThatThrownBy(() -> customRouter.chatWithSnapshot(
            "openai-chat-completions", "model-a",
            List.of(Map.of("role", "user", "content", "hello"))
        ))
            .isInstanceOf(com.interview.shared.api.BusinessException.class)
            .hasMessageContaining("自定义接口调用失败");

        // 关键：不应查询备用通道，备用 provider 不应被调用（其 Key 不会被复用）。
        verifyNoInteractionsWithFallbackConfig();
        assertThat(fallbackProvider.invocationCount).isZero();
    }

    @Test
    void fallbackUsesSystemKeyNotUserKey() {
        // 内置 provider 失败 → fallback 到另一内置 provider，且只用系统 Key（不得解密用户 BYOK Key）。
        FailingProvider failingProvider = new FailingProvider(
            "test", "Test", "model-a", new com.interview.shared.api.LlmServerException("upstream 500"));
        CapturingProvider fallbackProvider = new CapturingProvider("deepseek", "DeepSeek", "deepseek-v4-pro");
        LlmRouter customRouter = new LlmRouter(
            userMapper,
            llmProviderConfigMapper,
            aesGcmEncryptor,
            new ObjectMapper(),
            List.of(failingProvider, fallbackProvider),
            sseEmitterRegistry
        );

        User user = new User();
        user.setId(9L);
        user.setLlmApiKeyEncrypted("user-cipher"); // 用户旧 BYOK Key

        LlmProviderConfig mainConfig = new LlmProviderConfig();
        mainConfig.setProviderKey("test");
        mainConfig.setBaseUrl("https://example.test/chat");
        mainConfig.setAvailableModels("[\"model-a\"]");
        mainConfig.setEnabled(1);

        LlmProviderConfig fallbackConfig = new LlmProviderConfig();
        fallbackConfig.setProviderKey("deepseek");
        fallbackConfig.setBaseUrl("https://api.deepseek.com/chat/completions");
        fallbackConfig.setAvailableModels("[\"deepseek-v4-pro\"]");
        fallbackConfig.setEnabled(1);

        when(userMapper.selectById(9L)).thenReturn(user);
        // selectOne 用于主 provider 校验；fallback 路径用 selectList。
        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mainConfig);
        when(llmProviderConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(fallbackConfig));

        UserContext.setCurrentUserId(9L);
        customRouter.chatWithSnapshot(
            "test", "model-a",
            List.of(Map.of("role", "user", "content", "hello"))
        );

        // 备用 provider 收到的必须是系统 Key，绝不得把用户 BYOK Key 发给其他 provider。
        assertThat(fallbackProvider.lastInvocation.apiKey()).isEqualTo("system-key");
    }

    @Test
    void chatWithExplicitUsesSystemKeyForBuiltInProviderWhenApiKeyMissing() {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("test");
        config.setBaseUrl("https://example.test/chat");
        config.setAvailableModels("[\"model-a\"]");
        config.setEnabled(1);

        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        String result = router.chatWithExplicit(
            "test",
            "model-a",
            null,
            null,
            List.of(Map.of("role", "user", "content", "hello")),
            null,
            null
        );

        assertThat(result).isEqualTo("ok");
        assertThat(provider.lastInvocation.apiKey()).isEqualTo("system-key");
    }

    @Test
    void chatWithExplicitRejectsNonWhitelistedModelForBuiltInProvider() {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("test");
        config.setBaseUrl("https://example.test/chat");
        config.setAvailableModels("[\"model-a\"]");
        config.setEnabled(1);

        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        assertThatThrownBy(() -> router.chatWithExplicit(
            "test",
            "model-b",
            null,
            null,
            List.of(Map.of("role", "user", "content", "hello")),
            null,
            null
        ))
            .isInstanceOf(com.interview.shared.api.BusinessException.class)
            .hasMessageContaining("所选模型不在可用列表中");
        assertThat(provider.invocationCount).isZero();
    }

    @Test
    void chatWithExplicitRejectsCustomProviderWhenApiKeyMissing() {
        CapturingProvider customProvider = new CapturingProvider(
            "openai-chat-completions", "OpenAI Chat Completions", "");
        LlmRouter customRouter = new LlmRouter(
            userMapper,
            llmProviderConfigMapper,
            aesGcmEncryptor,
            new ObjectMapper(),
            List.of(customProvider),
            sseEmitterRegistry
        );

        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("openai-chat-completions");
        config.setBaseUrl("");
        config.setAvailableModels("[]");
        config.setEnabled(1);

        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        assertThatThrownBy(() -> customRouter.chatWithExplicit(
            "openai-chat-completions",
            "model-a",
            "https://example.com/v1",
            null,
            List.of(Map.of("role", "user", "content", "hello")),
            null,
            null
        ))
            .isInstanceOf(com.interview.shared.api.BusinessException.class)
            .hasMessageContaining("API Key");
        assertThat(customProvider.invocationCount).isZero();
    }

    @Test
    void chatWithExplicitPassesMaxTokensAndExtraParams() {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setProviderKey("test");
        config.setBaseUrl("https://example.test/chat");
        config.setAvailableModels("[\"model-a\"]");
        config.setEnabled(1);

        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        router.chatWithExplicit(
            "test",
            "model-a",
            null,
            null,
            List.of(Map.of("role", "user", "content", "hello")),
            8192,
            Map.of("thinking_depth", "high")
        );

        assertThat(provider.lastInvocation.maxTokens()).isEqualTo(8192);
        assertThat(provider.lastInvocation.extraParams()).containsEntry("thinking_depth", "high");
    }

@Test
    void fallbackExcludesCustomProviders() {
        FailingProvider failingProvider = new FailingProvider(
            "test", "Test", "model-a", new com.interview.shared.api.LlmServerException("upstream 500"));
        CapturingProvider customProvider = new CapturingProvider(
            "anthropic-messages", "Anthropic Messages", "");
        LlmRouter customRouter = new LlmRouter(
            userMapper,
            llmProviderConfigMapper,
            aesGcmEncryptor,
            new ObjectMapper(),
            List.of(failingProvider, customProvider),
            sseEmitterRegistry
        );

        User user = new User();
        user.setId(9L);

        LlmProviderConfig mainConfig = new LlmProviderConfig();
        mainConfig.setProviderKey("test");
        mainConfig.setBaseUrl("https://example.test/chat");
        mainConfig.setAvailableModels("[\"model-a\"]");
        mainConfig.setEnabled(1);

        LlmProviderConfig customConfig = new LlmProviderConfig();
        customConfig.setProviderKey("anthropic-messages");
        customConfig.setBaseUrl("https://attacker.example/v1");
        customConfig.setAvailableModels("[]");
        customConfig.setEnabled(1);

        when(userMapper.selectById(9L)).thenReturn(user);
        when(llmProviderConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mainConfig);
        // Mapper mock intentionally returns a custom row, simulating either a
        // misconfigured custom mapper or a future regression that drops the SQL .ne(...) guard.
        // The in-memory .filter(...) in listFallbackProviderConfigs must still exclude it.
        when(llmProviderConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(customConfig));

        UserContext.setCurrentUserId(9L);
        assertThatThrownBy(() -> customRouter.chatWithSnapshot(
            "test",
            "model-a",
            List.of(Map.of("role", "user", "content", "hello"))
        ))
            .isInstanceOf(com.interview.shared.api.BusinessException.class)
            .hasMessageContaining("无配置的可用备用通道");
        assertThat(customProvider.invocationCount).isZero();
    }

    private void verifyNoInteractionsWithFallbackConfig() {
        org.mockito.Mockito.verify(llmProviderConfigMapper, org.mockito.Mockito.never())
            .selectList(any(LambdaQueryWrapper.class));
    }

    private static final class CapturingProvider implements LlmProvider {
        private final String providerKey;
        private final String providerName;
        private final String defaultModel;
        private LlmInvocation lastInvocation;
        private int invocationCount = 0;

        private CapturingProvider() {
            this("test", "Test", "model-a");
        }

        private CapturingProvider(String providerKey, String providerName, String defaultModel) {
            this.providerKey = providerKey;
            this.providerName = providerName;
            this.defaultModel = defaultModel;
        }

        @Override
        public String providerKey() {
            return providerKey;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public String defaultModel() {
            return defaultModel;
        }

        @Override
        public String systemApiKey() {
            return "system-key";
        }

        @Override
        public String chat(LlmInvocation invocation) {
            this.lastInvocation = invocation;
            this.invocationCount++;
            return "ok";
        }

        @Override
        public void streamChat(LlmInvocation invocation, Consumer<String> onDelta) {
            this.lastInvocation = invocation;
            this.invocationCount++;
            onDelta.accept("ok");
        }
    }

    private static final class FailingProvider implements LlmProvider {
        private final String providerKey;
        private final String providerName;
        private final String defaultModel;
        private final RuntimeException failure;
        private int invocationCount = 0;

        private FailingProvider(String providerKey, String providerName, String defaultModel, RuntimeException failure) {
            this.providerKey = providerKey;
            this.providerName = providerName;
            this.defaultModel = defaultModel;
            this.failure = failure;
        }

        @Override
        public String providerKey() {
            return providerKey;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public String defaultModel() {
            return defaultModel;
        }

        @Override
        public String systemApiKey() {
            return "system-key";
        }

        @Override
        public String chat(LlmInvocation invocation) {
            this.invocationCount++;
            throw failure;
        }

        @Override
        public void streamChat(LlmInvocation invocation, Consumer<String> onDelta) {
            this.invocationCount++;
            throw failure;
        }
    }
}
