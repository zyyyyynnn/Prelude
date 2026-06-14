package com.interview.service;

import com.interview.dto.LlmConfigTestResponse;
import com.interview.dto.LlmModelDiscoveryRequest;
import com.interview.dto.LlmModelDiscoveryResponse;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;

public interface UserLlmConfigService {

    UserLlmConfigResponse getCurrentUserConfig();

    UserLlmConfigResponse updateCurrentUserConfig(UserLlmConfigRequest request);

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);

    LlmConfigTestResponse testCurrentUserConfig();
}
