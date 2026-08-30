package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.llm.api.LlmConfigTestRequest;
import com.prelude.llm.api.LlmConfigTestResponse;
import com.prelude.llm.api.LlmModelDiscoveryRequest;
import com.prelude.llm.api.LlmModelDiscoveryResponse;
import com.prelude.llm.api.UserLlmConfigRequest;
import com.prelude.llm.api.UserLlmConfigResponse;
import com.prelude.llm.LlmRouter;
import com.prelude.llm.LlmSelection;
import com.prelude.settings.AesGcmEncryptor;
import com.prelude.llm.LlmModelDiscoveryService;
import com.prelude.llm.UserLlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLlmConfigServiceImpl implements UserLlmConfigService {

    private final AccountMapper accountMapper;
    private final LlmRouter llmRouter;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final LlmModelDiscoveryService llmModelDiscoveryService;
    private final CustomLlmEgressPolicy egressPolicy;
    private final CurrentAccount currentAccount;

    @Override
    public UserLlmConfigResponse getCurrentUserConfig() {
        Account account = requireAccount();
        LlmSelection selection = llmRouter.resolveSelection(account.getId(), null);
        return new UserLlmConfigResponse(
            selection.providerKey(),
            account.getLlmBaseUrl(),
            selection.model(),
            account.getLlmApiKeyEncrypted() != null && !account.getLlmApiKeyEncrypted().isBlank(),
            maskApiKey(account.getLlmApiKeyEncrypted()),
            account.getLlmMaxTokens(),
            account.getLlmThinkingDepth()
        );
    }

    @Override
    public UserLlmConfigResponse updateCurrentUserConfig(UserLlmConfigRequest request) {
        Account account = requireAccount();
        String providerKey = request.providerKey();
        String baseUrl = null;
        if (CustomLlmProtocol.isCustom(providerKey)) {
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(request.baseUrl(), CustomLlmProtocol.require(providerKey));
            egressPolicy.validateConfiguredEndpoint(baseUrl);
        }
        llmRouter.validateProviderSelection(providerKey, request.model());

        boolean scopeChanged = isScopeChanged(account, providerKey, baseUrl);

        String encryptedApiKey = account.getLlmApiKeyEncrypted();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            encryptedApiKey = "__CLEAR__".equals(request.apiKey())
                ? null
                : aesGcmEncryptor.encrypt(request.apiKey());
        } else if (scopeChanged) {
            // 未提供新 Key 且 provider/baseUrl 已变：清空旧 Key，避免串用到新接入方式。
            encryptedApiKey = null;
        }

        accountMapper.updateLlmConfiguration(
            account.getId(),
            providerKey,
            baseUrl,
            request.model(),
            encryptedApiKey,
            request.maxTokens(),
            request.thinkingDepth()
        );

        return getCurrentUserConfig();
    }

    @Override
    public LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request) {
        Account account = requireAccount();
        CustomLlmProtocol protocol = CustomLlmProtocol.require(request.providerKey());
        String normalizedBaseUrl = CustomLlmEndpointUrl.normalizeRoot(request.baseUrl(), protocol);
        // 自动检测按与测试相同的 Key 选择规则处理：表单新 Key > 同 scope 已保存 Key > 否则报错。检测不保存 Key。
        String apiKey = resolveDraftApiKey(request.apiKey(), account, protocol.providerKey(), normalizedBaseUrl);
        return llmModelDiscoveryService.discoverModels(
            new LlmModelDiscoveryRequest(protocol.providerKey(), normalizedBaseUrl, apiKey));
    }

    @Override
    public LlmConfigTestResponse testCurrentUserConfig() {
        Account account = requireAccount();
        LlmSelection selection = llmRouter.resolveSelection(account.getId(), null);
        String content = llmRouter.chat(account.getId(), List.of(
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
        if (request == null || isAllBlank(request)) {
            return testCurrentUserConfig();
        }

        Account account = requireAccount();
        String providerKey = (request.providerKey() == null || request.providerKey().isBlank())
            ? account.getLlmProvider() : request.providerKey();
        String model = (request.model() == null || request.model().isBlank())
            ? account.getLlmModel() : request.model();
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
                if (!providerKey.equals(account.getLlmProvider())
                    || account.getLlmBaseUrl() == null || account.getLlmBaseUrl().isBlank()) {
                    throw BusinessException.badRequest("请填写 Base URL");
                }
                draftBaseUrl = account.getLlmBaseUrl();
            }
            baseUrl = CustomLlmEndpointUrl.normalizeRoot(draftBaseUrl, CustomLlmProtocol.require(providerKey));
            egressPolicy.validateConfiguredEndpoint(baseUrl);
        }

        String apiKey = resolveDraftApiKey(request.apiKey(), account, providerKey, baseUrl);
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

    private boolean isScopeChanged(Account account, String newProviderKey, String newBaseUrl) {
        if (newProviderKey == null || !newProviderKey.equals(account.getLlmProvider())) {
            return true;
        }
        if (CustomLlmProtocol.isCustom(newProviderKey)) {
            String oldBaseUrl = account.getLlmBaseUrl();
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
    private String resolveDraftApiKey(String draftApiKey, Account account, String providerKey, String baseUrl) {
        if (draftApiKey != null && !draftApiKey.isBlank()) {
            return draftApiKey;
        }
        boolean scopeChanged = isScopeChanged(account, providerKey, baseUrl);
        if (!scopeChanged) {
            return decryptSavedApiKey(account.getLlmApiKeyEncrypted());
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

    private Account requireAccount() {
        long accountId = currentAccount.requireId();
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return account;
    }

    private String maskApiKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.mask(encryptedApiKey);
        } catch (BusinessException exception) {
            log.warn("Failed to mask account API key");
            return null;
        }
    }
}
