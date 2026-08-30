package com.prelude.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.BusinessException;
import com.prelude.llm.persistence.LlmProviderConfig;
import com.prelude.llm.persistence.LlmProviderConfigMapper;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LlmSelectionResolver {

    private final AccountMapper accountMapper;
    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final ObjectMapper objectMapper;
    private final LlmProviderRegistry providerRegistry;

    public LlmSelection resolveSelection(Long accountId, String requestedModel) {
        Account account = requireAccount(accountId);
        LlmProvider provider = providerRegistry.require(account.getLlmProvider());
        String model = normalizeModel(
            requestedModel == null || requestedModel.isBlank() ? account.getLlmModel() : requestedModel,
            provider.defaultModel()
        );
        validateProviderSelection(provider.providerKey(), model);
        return new LlmSelection(provider.providerKey(), model);
    }

    public LlmProviderConfig validateProviderSelection(String providerKey, String model) {
        LlmProviderConfig providerConfig = requireEnabledProviderConfig(providerKey);
        LlmProvider provider = providerRegistry.require(providerKey);
        String normalizedModel = normalizeModel(model, provider.defaultModel());
        String availableModels = providerConfig.getAvailableModels();
        if (!CustomLlmProtocol.isCustom(providerKey)
            && availableModels != null
            && !availableModels.isBlank()) {
            List<String> models = parseModels(availableModels);
            if (!models.isEmpty() && !models.contains(normalizedModel)) {
                throw BusinessException.badRequest("所选模型不在可用列表中");
            }
        }
        if (normalizedModel == null || normalizedModel.isBlank()) {
            throw BusinessException.badRequest("模型不能为空");
        }
        return providerConfig;
    }

    private Account requireAccount(Long accountId) {
        if (accountId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return account;
    }

    private LlmProviderConfig requireEnabledProviderConfig(String providerKey) {
        LlmProviderConfig config = llmProviderConfigMapper.selectOne(new LambdaQueryWrapper<LlmProviderConfig>()
            .eq(LlmProviderConfig::getProviderKey, providerKey)
            .eq(LlmProviderConfig::getEnabled, 1)
            .last("LIMIT 1"));
        if (config == null) {
            throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换接入方式");
        }
        return config;
    }

    private List<String> parseModels(String availableModels) {
        try {
            return objectMapper.readValue(availableModels, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            throw BusinessException.badRequest("接入方式配置格式错误");
        }
    }

    private String normalizeModel(String model, String defaultModel) {
        return model == null || model.isBlank() ? defaultModel : model;
    }
}
