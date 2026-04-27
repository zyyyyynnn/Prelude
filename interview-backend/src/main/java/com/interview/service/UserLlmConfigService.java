package com.interview.service;

import com.interview.dto.LlmConfigTestResponse;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;

public interface UserLlmConfigService {

    UserLlmConfigResponse getCurrentUserConfig();

    UserLlmConfigResponse updateCurrentUserConfig(UserLlmConfigRequest request);

    LlmConfigTestResponse testCurrentUserConfig();
}
