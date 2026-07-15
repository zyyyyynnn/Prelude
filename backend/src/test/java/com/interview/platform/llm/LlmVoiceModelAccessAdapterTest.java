package com.interview.platform.llm;

import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.shared.web.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmVoiceModelAccessAdapterTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AesGcmEncryptor encryptor = mock(AesGcmEncryptor.class);

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void usesSelectedOpenAiCustomRootAndUserKey() {
        User user = user("openai-responses", "https://proxy.example/v1/responses", "cipher");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(encryptor.decrypt("cipher")).thenReturn("sk-user");
        UserContext.setCurrentUserId(7L);

        LlmVoiceModelAccessAdapter adapter = new LlmVoiceModelAccessAdapter(
            userMapper, encryptor, "https://api.openai.com/v1", "sk-system");

        VoiceModelAccessPort.VoiceModelAccess access = adapter.resolveCurrentUser();

        assertThat(access.baseUrl()).isEqualTo("https://proxy.example/v1");
        assertThat(access.apiKey()).isEqualTo("sk-user");
    }

    @Test
    void neverSendsAnthropicKeyToOpenAiVoiceEndpoint() {
        User user = user("anthropic-messages", "https://api.anthropic.com/v1", "anthropic-cipher");
        when(userMapper.selectById(7L)).thenReturn(user);
        UserContext.setCurrentUserId(7L);

        LlmVoiceModelAccessAdapter adapter = new LlmVoiceModelAccessAdapter(
            userMapper, encryptor, "https://api.openai.com/v1", "sk-system");

        VoiceModelAccessPort.VoiceModelAccess access = adapter.resolveCurrentUser();

        assertThat(access.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(access.apiKey()).isEqualTo("sk-system");
        verify(encryptor, never()).decrypt("anthropic-cipher");
    }

    private User user(String providerKey, String baseUrl, String encryptedApiKey) {
        User user = new User();
        user.setId(7L);
        user.setLlmProvider(providerKey);
        user.setLlmBaseUrl(baseUrl);
        user.setLlmApiKeyEncrypted(encryptedApiKey);
        return user;
    }
}
