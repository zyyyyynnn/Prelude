package com.prelude.identity.web;

import com.prelude.Result;
import com.prelude.identity.api.UserProfileRequest;
import com.prelude.identity.api.UserProfileResponse;
import com.prelude.identity.application.ProfileService;
import com.prelude.identity.application.port.AvatarUpload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
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

    private final ProfileService profileService;

    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        return Result.success(profileService.getCurrentUserProfile());
    }

    @PutMapping("/profile")
    public Result<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileRequest request) {
        return Result.success(profileService.updateCurrentUserProfile(request));
    }

    @PostMapping("/avatar")
    public Result<UserProfileResponse> updateAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(profileService.updateAvatar(toUpload(file)));
    }

    /** Reads the upload's bytes here, so the use case never names the multipart type. */
    private AvatarUpload toUpload(MultipartFile file) {
        try {
            return new AvatarUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new com.prelude.BusinessException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "avatar_unreadable", "头像读取失败");
        }
    }
}
