package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.dto.UserProfileRequest;
import com.interview.dto.UserProfileResponse;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserLlmConfigService userLlmConfigService;
    private final UserProfileService userProfileService;

    @GetMapping("/llm-config")
    public Result<UserLlmConfigResponse> getLlmConfig() {
        return Result.success(userLlmConfigService.getCurrentUserConfig());
    }

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        return Result.success(userProfileService.getCurrentUserProfile());
    }

    @PutMapping("/llm-config")
    public Result<UserLlmConfigResponse> updateLlmConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        return Result.success(userLlmConfigService.updateCurrentUserConfig(request));
    }

    @PutMapping("/profile")
    public Result<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileRequest request) {
        return Result.success(userProfileService.updateCurrentUserProfile(request));
    }
}
