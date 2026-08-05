package com.interview.identity.application.port;

public interface AvatarContentProcessor {

    ProcessedAvatar process(AvatarUpload upload);

    default ProcessedAvatar processLegacy(AvatarUpload upload) {
        return process(upload);
    }
}
