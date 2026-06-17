package com.interview.service;

import com.interview.dto.UserProfileRequest;
import com.interview.dto.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UserProfileRequest request);

    UserProfileResponse updateAvatar(MultipartFile file);
}
