package com.prelude.llm;

import com.prelude.llm.api.LlmModelDiscoveryRequest;
import com.prelude.llm.api.LlmModelDiscoveryResponse;

public interface LlmModelDiscoveryService {

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);
}
