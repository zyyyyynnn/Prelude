package com.interview.service.impl;

import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.LlmConfigTestRequest;
import com.interview.dto.LlmConfigTestResponse;
import com.interview.dto.LlmModelDiscoveryRequest;
import com.interview.dto.LlmModelDiscoveryResponse;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.entity.User;
import com.interview.llm.LlmRouter;
import com.interview.llm.LlmSelection;
import com.interview.llm.OpenAiCompatibleProvider;
import com.interview.llm.OpenAiCompatibleUrl;
import com.interview.mapper.UserMapper;
import com.interview.security.AesGcmEncryptor;
import com.interview.service.DemoModeService;
import com.interview.service.LlmModelDiscoveryService;
import com.interview.service.UserLlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLlmConfigServiceImpl implements UserLlmConfigService {

    private final UserMapper userMapper;
    private final LlmRouter llmRouter;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final DemoModeService demoModeService;
    private final LlmModelDiscoveryService llmModelDiscoveryService;

    @Override
    public UserLlmConfigResponse getCurrentUserConfig() {
        User user = requireCurrentUser();
        LlmSelection selection = llmRouter.resolveCurrentUserSelection();
        return new UserLlmConfigResponse(
            selection.providerKey(),
            user.getLlmBaseUrl(),
            selection.model(),
            user.getLlmApiKeyEncrypted() != null && !user.getLlmApiKeyEncrypted().isBlank(),
            maskApiKey(user.getLlmApiKeyEncrypted()),
            user.getLlmMaxTokens(),
            user.getLlmThinkingDepth()
        );
    }

    @Override
    public UserLlmConfigResponse updateCurrentUserConfig(UserLlmConfigRequest request) {
        User user = requireCurrentUser();
        String providerKey = request.providerKey();
        String baseUrl = null;
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(providerKey)) {
            baseUrl = OpenAiCompatibleUrl.normalizeRoot(request.baseUrl());
        }
        llmRouter.validateProviderSelection(providerKey, request.model());

        // scope 是否相对旧配置发生变化（provider 或 openai-compatible 的归一化 baseUrl）。
        boolean scopeChanged = isScopeChanged(user, providerKey, baseUrl);

        String encryptedApiKey = user.getLlmApiKeyEncrypted();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            encryptedApiKey = isDemoEnabled()
                ? demoModeService.nextStoredApiKey(request.apiKey(), encryptedApiKey)
                : "__CLEAR__".equals(request.apiKey())
                    ? null
                    : aesGcmEncryptor.encrypt(request.apiKey());
        } else if (scopeChanged) {
            // 未提供新 Key 且 provider/baseUrl 已变：清空旧 Key，避免串用到新接入方式。
            encryptedApiKey = null;
        }

        user.setLlmProvider(providerKey);
        user.setLlmBaseUrl(baseUrl);
        user.setLlmModel(request.model());
        user.setLlmApiKeyEncrypted(encryptedApiKey);
        user.setLlmMaxTokens(request.maxTokens());
        user.setLlmThinkingDepth(request.thinkingDepth());
        userMapper.updateById(user);

        return getCurrentUserConfig();
    }

    @Override
    public LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request) {
        User user = requireCurrentUser();
        String normalizedBaseUrl = OpenAiCompatibleUrl.normalizeRoot(request.baseUrl());
        // 自动检测按与测试相同的 Key 选择规则处理：表单新 Key > 同 scope 已保存 Key > 否则报错。检测不保存 Key。
        String apiKey = resolveDraftApiKey(request.apiKey(), user, OpenAiCompatibleProvider.PROVIDER_KEY, normalizedBaseUrl);
        return llmModelDiscoveryService.discoverModels(new LlmModelDiscoveryRequest(normalizedBaseUrl, apiKey));
    }

    @Override
    public LlmConfigTestResponse testCurrentUserConfig() {
        LlmSelection selection = llmRouter.resolveCurrentUserSelection();
        if (isDemoEnabled()) {
            return new LlmConfigTestResponse(selection.providerKey(), selection.model(), true, "Demo 模式配置可用");
        }

        String content = llmRouter.chatCurrentUser(List.of(
            Map.of("role", "system", "content", "你是模型连通性测试助手。"),
            Map.of("role", "user", "content", "请只回复 OK")
        ));
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        return new LlmConfigTestResponse(selection.providerKey(), selection.model(), true, "模型配置测试通过");
    }

    @Override
    public LlmConfigTestResponse testConfig(LlmConfigTestRequest request) {
        // 无 body 或全空：回退到测试已保存配置（向后兼容）。
        if (request == null || isAllBlank(request)) {
            return testCurrentUserConfig();
        }

        User user = requireCurrentUser();
        String providerKey = (request.providerKey() == null || request.providerKey().isBlank())
            ? user.getLlmProvider() : request.providerKey();
        String model = (request.model() == null || request.model().isBlank())
            ? user.getLlmModel() : request.model();
        if (providerKey == null || providerKey.isBlank()) {
            throw BusinessException.badRequest("请选择接入方式");
        }
        if (model == null || model.isBlank()) {
            throw BusinessException.badRequest("请选择模型");
        }

        String baseUrl = null;
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(providerKey)) {
            baseUrl = OpenAiCompatibleUrl.normalizeRoot(request.baseUrl());
        }

        if (isDemoEnabled()) {
            return new LlmConfigTestResponse(providerKey, model, true, "Demo 模式配置可用");
        }

        String apiKey = resolveDraftApiKey(request.apiKey(), user, providerKey, baseUrl);
        String content = llmRouter.chatWithExplicit(providerKey, model, baseUrl, apiKey, List.of(
            Map.of("role", "system", "content", "你是模型连通性测试助手。"),
            Map.of("role", "user", "content", "请只回复 OK")
        ));
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        return new LlmConfigTestResponse(providerKey, model, true, "模型配置测试通过");
    }

    private boolean isAllBlank(LlmConfigTestRequest request) {
        return isBlank(request.providerKey()) && isBlank(request.baseUrl())
            && isBlank(request.model()) && isBlank(request.apiKey())
            && request.maxTokens() == null && isBlank(request.thinkingDepth());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * scope 是否相对旧配置发生变化：provider 变更，或 openai-compatible 的归一化 baseUrl 变更。
     */
    private boolean isScopeChanged(User user, String newProviderKey, String newBaseUrl) {
        if (newProviderKey == null || !newProviderKey.equals(user.getLlmProvider())) {
            return true;
        }
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(newProviderKey)) {
            String oldBaseUrl = user.getLlmBaseUrl();
            return newBaseUrl == null || !newBaseUrl.equals(oldBaseUrl);
        }
        return false;
    }

    /**
     * 草稿 Key 选择规则：
     * - 表单新 Key 非空 → 用新 Key。
     * - 无新 Key 且 scope 未变 → 解密已保存 BYOK Key。
     * - 无新 Key 且 scope 已变：
     *   - openai-compatible → 报错（必须重新填 Key）。
     *   - 内置 provider → 不复用旧 BYOK Key，由 LlmRouter 回退该 provider 系统 Key（传 null）。
     */
    private String resolveDraftApiKey(String draftApiKey, User user, String providerKey, String baseUrl) {
        if (draftApiKey != null && !draftApiKey.isBlank()) {
            return draftApiKey;
        }
        boolean scopeChanged = isScopeChanged(user, providerKey, baseUrl);
        if (!scopeChanged) {
            return decryptSavedApiKey(user.getLlmApiKeyEncrypted());
        }
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(providerKey)) {
            throw BusinessException.badRequest("更换接入方式或 Base URL 后，请重新填写 API Key 再测试。");
        }
        return null;
    }

    private String decryptSavedApiKey(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.decrypt(encrypted);
        } catch (BusinessException exception) {
            log.warn("Failed to decrypt saved API key for draft test");
            return null;
        }
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
