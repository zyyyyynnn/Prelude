package com.interview.service.impl;

import com.interview.dto.UserLlmConfigResponse;
import com.interview.entity.User;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLlmConfigServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private AesGcmEncryptor aesGcmEncryptor;

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
        user.setLlmApiKeyEncrypted("cipher-text");

        when(userMapper.selectById(7L)).thenReturn(user);
        when(llmRouter.resolveCurrentUserSelection()).thenReturn(new LlmSelection("openai", "gpt-4o"));
        when(aesGcmEncryptor.mask("cipher-text")).thenReturn("****1234");

        com.interview.common.UserContext.setCurrentUserId(7L);
        UserLlmConfigResponse response = service.getCurrentUserConfig();

        assertThat(response.providerKey()).isEqualTo("openai");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.apiKeyMasked()).isEqualTo("****1234");
        verify(aesGcmEncryptor).mask("cipher-text");
    }
}
