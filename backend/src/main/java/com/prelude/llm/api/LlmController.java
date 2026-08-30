package com.prelude.llm.api;

import com.prelude.Result;
import com.prelude.llm.api.LlmProviderResponse;
import com.prelude.llm.LlmConfigPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmConfigPort llmConfigPort;

    @GetMapping("/providers")
    public Result<List<LlmProviderResponse>> providers() {
        return Result.success(llmConfigPort.listProviders());
    }

    @GetMapping("/config")
    public Result<UserLlmConfigResponse> config() {
        return Result.success(llmConfigPort.getCurrentUserConfig());
    }

    @PutMapping("/config")
    public Result<UserLlmConfigResponse> updateConfig(@jakarta.validation.Valid @RequestBody UserLlmConfigRequest request) {
        return Result.success(llmConfigPort.saveCurrentUserConfig(request));
    }

    @PostMapping("/config/test")
    public Result<LlmConfigTestResponse> testConfig(@RequestBody(required = false) LlmConfigTestRequest request) {
        return Result.success(llmConfigPort.testConfig(request));
    }

    @PostMapping("/config/discover-models")
    public Result<LlmModelDiscoveryResponse> discoverModels(@jakarta.validation.Valid @RequestBody LlmModelDiscoveryRequest request) {
        return Result.success(llmConfigPort.discoverModels(request));
    }
}
