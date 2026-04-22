package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.entity.User;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import com.interview.service.DemoModeService;
import com.interview.service.UserLlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLlmConfigServiceImpl implements UserLlmConfigService {

    private final UserMapper userMapper;
    private final LlmRouter llmRouter;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final DemoModeService demoModeService;

    @Override
    public UserLlmConfigResponse getCurrentUserConfig() {
        User user = requireCurrentUser();
        LlmSelection selection = llmRouter.resolveCurrentUserSelection();
        return new UserLlmConfigResponse(
            selection.providerKey(),
            selection.model(),
            maskApiKey(user.getLlmApiKeyEncrypted())
        );
    }

    @Override
    public UserLlmConfigResponse updateCurrentUserConfig(UserLlmConfigRequest request) {
        User user = requireCurrentUser();
        llmRouter.validateProviderSelection(request.providerKey(), request.model());

        String encryptedApiKey = user.getLlmApiKeyEncrypted();
        if (request.apiKey() != null) {
            encryptedApiKey = isDemoEnabled()
                ? demoModeService.nextStoredApiKey(request.apiKey(), encryptedApiKey)
                : request.apiKey().isBlank() ? null : aesGcmEncryptor.encrypt(request.apiKey());
        }

        userMapper.update(
            null,
            new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLlmProvider, request.providerKey())
                .set(User::getLlmModel, request.model())
                .set(User::getLlmApiKeyEncrypted, encryptedApiKey)
        );

        return getCurrentUserConfig();
    }

    private User requireCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user;
    }

    private String maskApiKey(String encryptedApiKey) {
        if (isDemoEnabled()) {
            return demoModeService.maskApiKey(encryptedApiKey);
        }
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.mask(encryptedApiKey);
        } catch (BusinessException exception) {
            log.warn("Failed to mask user API key");
            return null;
        }
    }

    private boolean isDemoEnabled() {
        return demoModeService != null && demoModeService.isEnabled();
    }
}
