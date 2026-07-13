package com.interview.platform.llm;

import com.interview.platform.llm.api.LlmConfigTestRequest;
import com.interview.platform.llm.api.LlmConfigTestResponse;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import com.interview.platform.llm.api.LlmProviderResponse;
import com.interview.platform.llm.api.UserLlmConfigRequest;
import com.interview.platform.llm.api.UserLlmConfigResponse;
import com.interview.platform.llm.LlmSelection;

import java.util.List;

public interface LlmConfigPort {

    LlmSelection resolveSelection(Long userId, String requestedModel);

    List<LlmProviderResponse> listProviders();

    UserLlmConfigResponse getCurrentUserConfig();

    UserLlmConfigResponse saveCurrentUserConfig(UserLlmConfigRequest request);

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);

    LlmConfigTestResponse testConfig(LlmConfigTestRequest request);
}
