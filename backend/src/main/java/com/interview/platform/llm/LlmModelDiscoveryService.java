package com.interview.platform.llm;

import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;

public interface LlmModelDiscoveryService {

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);
}
