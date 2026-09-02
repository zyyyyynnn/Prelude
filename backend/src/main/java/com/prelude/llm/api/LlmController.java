package com.prelude.llm.api;

import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
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

    private final LlmPort llmPort;
    private final CurrentAccount currentAccount;

    @GetMapping("/providers")
    public Result<List<ProviderDescriptorView>> providers() {
        return Result.success(llmPort.listModels(currentAccount.requireId()));
    }

    @GetMapping("/config")
    public Result<ModelConfigurationView> config() {
        return Result.success(llmPort.currentConfiguration(currentAccount.requireId()));
    }

    @PutMapping("/config")
    public Result<ModelConfigurationView> updateConfig(
        @jakarta.validation.Valid @RequestBody UpdateConfigurationRequest request) {
        return Result.success(llmPort.saveConfiguration(currentAccount.requireId(),
            new SaveConfigurationCommand(
                request.provider(),
                request.model(),
                request.customEndpointUrl(),
                request.apiKey(),
                request.reasoningLevel(),
                request.fallbackModels()
            )));
    }

    @PostMapping("/config/discover-models")
    public Result<LlmPort.DiscoveredModelsView> discoverModels(
        @jakarta.validation.Valid @RequestBody DiscoverModelsRequest request) {
        return Result.success(llmPort.discoverCustomModels(currentAccount.requireId(),
            new LlmPort.DiscoverModelsCommand(request.provider(), request.baseUrl(), request.apiKey())));
    }

    @PostMapping("/config/discover-capabilities")
    public Result<ModelCapabilityResponse> discoverCapabilities(
        @jakarta.validation.Valid @RequestBody DiscoverModelCapabilityRequest request) {
        return Result.success(llmPort.discoverCustomModelCapability(currentAccount.requireId(),
            new LlmPort.DiscoverModelCapabilityCommand(
                request.provider(), request.baseUrl(), request.apiKey(), request.model())));
    }

    public record UpdateConfigurationRequest(
        @jakarta.validation.constraints.NotBlank(message = "provider 不能为空")
        String provider,
        @jakarta.validation.constraints.NotBlank(message = "model 不能为空")
        String model,
        String customEndpointUrl,
        String apiKey,
        String reasoningLevel,
        List<String> fallbackModels
    ) {
    }

    public record DiscoverModelsRequest(
        @jakarta.validation.constraints.NotBlank(message = "provider 不能为空")
        String provider,
        @jakarta.validation.constraints.NotBlank(message = "baseUrl 不能为空")
        String baseUrl,
        String apiKey
    ) {
    }

    public record DiscoverModelCapabilityRequest(
        @jakarta.validation.constraints.NotBlank(message = "provider 不能为空")
        String provider,
        @jakarta.validation.constraints.NotBlank(message = "baseUrl 不能为空")
        String baseUrl,
        String apiKey,
        @jakarta.validation.constraints.NotBlank(message = "model 不能为空")
        String model
    ) {
    }
}
