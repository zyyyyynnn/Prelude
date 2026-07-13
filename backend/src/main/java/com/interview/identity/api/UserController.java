package com.interview.identity.api;

import com.interview.shared.api.Result;
import com.interview.platform.llm.api.LlmConfigTestRequest;
import com.interview.platform.llm.api.LlmConfigTestResponse;
import com.interview.platform.llm.api.LlmModelDiscoveryRequest;
import com.interview.platform.llm.api.LlmModelDiscoveryResponse;
import com.interview.platform.llm.api.UserLlmConfigRequest;
import com.interview.platform.llm.api.UserLlmConfigResponse;
import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import com.interview.platform.llm.LlmConfigPort;
import com.interview.identity.application.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final LlmConfigPort llmConfigPort;
    private final UserProfileService userProfileService;

    @GetMapping("/llm-config")
    public Result<UserLlmConfigResponse> getLlmConfig() {
        return Result.success(llmConfigPort.getCurrentUserConfig());
    }

    @PostMapping("/llm-config/test")
    public Result<LlmConfigTestResponse> testLlmConfig(@RequestBody(required = false) LlmConfigTestRequest request) {
        return Result.success(llmConfigPort.testConfig(request));
    }

    @PostMapping("/llm-config/discover-models")
    public Result<LlmModelDiscoveryResponse> discoverModels(@Valid @RequestBody LlmModelDiscoveryRequest request) {
        return Result.success(llmConfigPort.discoverModels(request));
    }

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        return Result.success(userProfileService.getCurrentUserProfile());
    }

    @PutMapping("/llm-config")
    public Result<UserLlmConfigResponse> updateLlmConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        return Result.success(llmConfigPort.saveCurrentUserConfig(request));
    }

    @PutMapping("/profile")
    public Result<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileRequest request) {
        return Result.success(userProfileService.updateCurrentUserProfile(request));
    }

    @PostMapping("/avatar")
    public Result<UserProfileResponse> updateAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(userProfileService.updateAvatar(file));
    }
}
