package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.UserLlmConfigRequest;
import com.interview.dto.UserLlmConfigResponse;
import com.interview.service.UserLlmConfigService;
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

    @GetMapping("/llm-config")
    public Result<UserLlmConfigResponse> getLlmConfig() {
        return Result.success(userLlmConfigService.getCurrentUserConfig());
    }

    @PutMapping("/llm-config")
    public Result<UserLlmConfigResponse> updateLlmConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        return Result.success(userLlmConfigService.updateCurrentUserConfig(request));
    }
}
