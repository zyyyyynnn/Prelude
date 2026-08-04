package com.interview.identity.application;

import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import com.interview.identity.application.port.AvatarUpload;

public interface UserProfileService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UserProfileRequest request);

    UserProfileResponse updateAvatar(AvatarUpload upload);
}
