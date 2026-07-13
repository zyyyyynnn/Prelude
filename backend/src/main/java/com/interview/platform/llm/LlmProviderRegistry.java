package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import com.interview.platform.llm.LlmProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmProviderRegistry {

    private final List<LlmProvider> providers;

    public LlmProviderRegistry(List<LlmProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public LlmProvider require(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            throw BusinessException.badRequest("接入方式不能为空");
        }
        return providers.stream()
            .filter(provider -> provider.providerKey().equalsIgnoreCase(providerKey))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换接入方式"));
    }
}
