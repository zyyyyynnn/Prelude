package com.interview.identity.application;

import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UserProfileRequest request);

    UserProfileResponse updateAvatar(MultipartFile file);
}
