package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.llm.api.LlmConfigTestRequest;
import com.interview.platform.llm.api.LlmConfigTestResponse;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import com.interview.platform.llm.api.UserLlmConfigRequest;
import com.interview.platform.llm.api.UserLlmConfigResponse;
import com.interview.identity.domain.User;
import com.interview.platform.llm.LlmRouter;
import com.interview.platform.llm.LlmSelection;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.platform.llm.LlmModelDiscoveryService;
import com.interview.platform.llm.UserLlmConfigService;
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
    private final LlmFixturePort devFixtureService;
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
        if (CustomLlmProtocol.isCustom(providerKey)) {
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(request.baseUrl(), CustomLlmProtocol.require(providerKey));
        }
        llmRouter.validateProviderSelection(providerKey, request.model());

        // scope 是否相对旧配置发生变化（provider 或自定义接口的归一化 baseUrl）。
        boolean scopeChanged = isScopeChanged(user, providerKey, baseUrl);

        String encryptedApiKey = user.getLlmApiKeyEncrypted();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            encryptedApiKey = "__CLEAR__".equals(request.apiKey())
                ? null
                : isDevFixtureEnabled()
                    ? devFixtureService.nextStoredApiKey(request.apiKey(), encryptedApiKey)
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
        CustomLlmProtocol protocol = CustomLlmProtocol.require(request.providerKey());
        String normalizedBaseUrl = CustomLlmEndpointUrl.normalizeRoot(request.baseUrl(), protocol);
        // 自动检测按与测试相同的 Key 选择规则处理：表单新 Key > 同 scope 已保存 Key > 否则报错。检测不保存 Key。
        String apiKey = resolveDraftApiKey(request.apiKey(), user, protocol.providerKey(), normalizedBaseUrl);
        return llmModelDiscoveryService.discoverModels(
            new LlmModelDiscoveryRequest(protocol.providerKey(), normalizedBaseUrl, apiKey));
    }

    @Override
    public LlmConfigTestResponse testCurrentUserConfig() {
        LlmSelection selection = llmRouter.resolveCurrentUserSelection();
        if (isDevFixtureEnabled()) {
            return new LlmConfigTestResponse(selection.providerKey(), selection.model(), true, "Dev fixture 配置可用");
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
        if (CustomLlmProtocol.isCustom(providerKey)) {
            String draftBaseUrl = request.baseUrl();
            if (draftBaseUrl == null || draftBaseUrl.isBlank()) {
                if (!providerKey.equals(user.getLlmProvider())
                    || user.getLlmBaseUrl() == null || user.getLlmBaseUrl().isBlank()) {
                    throw BusinessException.badRequest("请填写 Base URL");
                }
                draftBaseUrl = user.getLlmBaseUrl();
            }
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(draftBaseUrl, CustomLlmProtocol.require(providerKey));
        }

        if (isDevFixtureEnabled()) {
            return new LlmConfigTestResponse(providerKey, model, true, "Dev fixture 配置可用");
        }

        String apiKey = resolveDraftApiKey(request.apiKey(), user, providerKey, baseUrl);
        Map<String, Object> extraParams = null;
        if (request.thinkingDepth() != null && !request.thinkingDepth().isBlank()) {
            extraParams = Map.of("thinking_depth", request.thinkingDepth());
        }
        String content = llmRouter.chatWithExplicit(providerKey, model, baseUrl, apiKey, List.of(
            Map.of("role", "system", "content", "你是模型连通性测试助手。"),
            Map.of("role", "user", "content", "请只回复 OK")
        ), request.maxTokens(), extraParams);
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
     * scope 是否相对旧配置发生变化：provider 变更，或自定义接口的归一化 baseUrl 变更。
     */
    private boolean isScopeChanged(User user, String newProviderKey, String newBaseUrl) {
        if (newProviderKey == null || !newProviderKey.equals(user.getLlmProvider())) {
            return true;
        }
        if (CustomLlmProtocol.isCustom(newProviderKey)) {
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
     *   - 自定义接口 → 报错（必须重新填 Key）。
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
        if (CustomLlmProtocol.isCustom(providerKey)) {
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
        if (isDevFixtureEnabled()) {
            return devFixtureService.maskApiKey(encryptedApiKey);
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

    private boolean isDevFixtureEnabled() {
        return devFixtureService != null && devFixtureService.isEnabled();
    }
}
