package com.interview.service;

import com.interview.dto.LlmModelDiscoveryRequest;
import com.interview.dto.LlmModelDiscoveryResponse;

public interface LlmModelDiscoveryService {

    LlmModelDiscoveryResponse discoverModels(LlmModelDiscoveryRequest request);
}
