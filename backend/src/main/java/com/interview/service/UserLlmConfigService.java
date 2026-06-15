package com.interview.service;

import com.interview.dto.LlmConfigTestRequest;
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

    /**
     * 测试草稿配置（当前表单内容）。request 为 null 或全空时回退到测试已保存配置，不保存草稿。
     */
    LlmConfigTestResponse testConfig(LlmConfigTestRequest request);
}
