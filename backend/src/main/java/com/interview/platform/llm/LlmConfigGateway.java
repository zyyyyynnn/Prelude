package com.interview.platform.llm;

import com.interview.shared.web.UserContext;
import com.interview.platform.llm.api.LlmConfigTestRequest;
import com.interview.platform.llm.api.LlmConfigTestResponse;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import com.interview.platform.llm.api.LlmProviderResponse;
import com.interview.platform.llm.api.UserLlmConfigRequest;
import com.interview.platform.llm.api.UserLlmConfigResponse;
import com.interview.platform.llm.LlmRouter;
import com.interview.platform.llm.LlmSelection;
import com.interview.platform.llm.UserLlmConfigService;
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
