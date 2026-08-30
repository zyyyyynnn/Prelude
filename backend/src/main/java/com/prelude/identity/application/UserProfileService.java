package com.prelude.identity.application;

import com.prelude.identity.api.UserProfileRequest;
import com.prelude.identity.api.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UserProfileRequest request);

    UserProfileResponse updateAvatar(MultipartFile file);
}
