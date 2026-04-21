package com.interview.service;

import com.interview.dto.UserProfileRequest;
import com.interview.dto.UserProfileResponse;

public interface UserProfileService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UserProfileRequest request);
}
