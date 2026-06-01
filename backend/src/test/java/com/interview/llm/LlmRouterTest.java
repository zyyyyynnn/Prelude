package com.interview.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.UserContext;
import com.interview.entity.LlmProviderConfig;
import com.interview.entity.User;
import com.interview.mapper.LlmProviderConfigMapper;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRouterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final LlmProviderConfigMapper llmProviderConfigMapper = mock(LlmProviderConfigMapper.class);
    private final AesGcmEncryptor aesGcmEncryptor = mock(AesGcmEncryptor.class);
    private final CapturingProvider provider = new CapturingProvider();
    private final LlmRouter router = new LlmRouter(
        userMapper,
        llmProviderConfigMapper,
        aesGcmEncryptor,
        new ObjectMapper(),
        List.of(provider)
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

    private static final class CapturingProvider implements LlmProvider {
        private LlmInvocation lastInvocation;

        @Override
        public String providerKey() {
            return "test";
        }

        @Override
        public String providerName() {
            return "Test";
        }

        @Override
        public String defaultModel() {
            return "model-a";
        }

        @Override
        public String systemApiKey() {
            return "system-key";
        }

        @Override
        public String chat(LlmInvocation invocation) {
            this.lastInvocation = invocation;
            return "ok";
        }

        @Override
        public void streamChat(LlmInvocation invocation, Consumer<String> onDelta) {
            this.lastInvocation = invocation;
            onDelta.accept("ok");
        }
    }
}
