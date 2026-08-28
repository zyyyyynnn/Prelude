package com.prelude.llm;

import com.prelude.llm.api.LlmConfigTestRequest;
import com.prelude.llm.api.LlmConfigTestResponse;
import com.prelude.llm.api.LlmModelDiscoveryRequest;
import com.prelude.llm.api.LlmModelDiscoveryResponse;
import com.prelude.llm.api.LlmProviderResponse;
import com.prelude.llm.api.UserLlmConfigRequest;
import com.prelude.llm.api.UserLlmConfigResponse;
import com.prelude.llm.LlmSelection;

import java.util.List;

public interface LlmConfigPort {

    LlmSelection resolveSelection(Long userId, String requestedModel);

    String currentThinkingDepth();

    List<LlmProviderResponse> listProviders();

    UserLlmConfigResponse getCurrentUserConfig();

    UserLlmConfigResponse saveCurrentUserConfig(UserLlmConfigRequest request);

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);

    LlmConfigTestResponse testConfig(LlmConfigTestRequest request);
}
