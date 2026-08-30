package com.prelude.llm;

import com.prelude.UserContext;
import com.prelude.llm.api.LlmConfigTestRequest;
import com.prelude.llm.api.LlmConfigTestResponse;
import com.prelude.llm.api.LlmModelDiscoveryRequest;
import com.prelude.llm.api.LlmModelDiscoveryResponse;
import com.prelude.llm.api.LlmProviderResponse;
import com.prelude.llm.api.UserLlmConfigRequest;
import com.prelude.llm.api.UserLlmConfigResponse;
import com.prelude.llm.LlmRouter;
import com.prelude.llm.LlmSelection;
import com.prelude.llm.UserLlmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmConfigGateway implements LlmConfigPort {

    private final LlmRouter llmRouter;
    private final UserLlmConfigService userLlmConfigService;

    @Override
    public LlmSelection resolveSelection(Long userId, String requestedModel) {
        Long previousUserId = UserContext.getCurrentUserId();
        if (userId != null) {
            UserContext.setCurrentUserId(userId);
        }
        try {
            return llmRouter.resolveCurrentUserSelection(requestedModel);
        } finally {
            UserContext.remove();
            if (previousUserId != null) {
                UserContext.setCurrentUserId(previousUserId);
            }
        }
    }

    @Override
    public String currentThinkingDepth() {
        return userLlmConfigService.getCurrentUserConfig().thinkingDepth();
    }

    @Override
    public List<LlmProviderResponse> listProviders() {
        return llmRouter.listEnabledProviders();
    }

    @Override
    public UserLlmConfigResponse getCurrentUserConfig() {
        return userLlmConfigService.getCurrentUserConfig();
    }

    @Override
    public UserLlmConfigResponse saveCurrentUserConfig(UserLlmConfigRequest request) {
        return userLlmConfigService.updateCurrentUserConfig(request);
    }

    @Override
    public LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request) {
        return userLlmConfigService.discoverModels(request);
    }

    @Override
    public LlmConfigTestResponse testConfig(LlmConfigTestRequest request) {
        return userLlmConfigService.testConfig(request);
    }
}
