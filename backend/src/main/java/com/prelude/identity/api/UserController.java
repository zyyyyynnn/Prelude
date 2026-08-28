package com.prelude.identity.api;

import com.prelude.Result;
import com.prelude.identity.api.UserProfileRequest;
import com.prelude.identity.api.UserProfileResponse;
import com.prelude.identity.application.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        return Result.success(userProfileService.getCurrentUserProfile());
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
