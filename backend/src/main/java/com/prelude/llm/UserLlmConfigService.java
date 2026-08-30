package com.prelude.llm;

import com.prelude.llm.api.LlmConfigTestRequest;
import com.prelude.llm.api.LlmConfigTestResponse;
import com.prelude.llm.api.LlmModelDiscoveryRequest;
import com.prelude.llm.api.LlmModelDiscoveryResponse;
import com.prelude.llm.api.UserLlmConfigRequest;
import com.prelude.llm.api.UserLlmConfigResponse;

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
