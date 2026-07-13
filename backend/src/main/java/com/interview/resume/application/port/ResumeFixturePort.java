package com.interview.resume.application.port;

import com.interview.resume.api.ResumeUploadResponse;

public interface ResumeFixturePort {

    boolean isEnabled();

    ResumeUploadResponse createDevFixtureResume(Long userId, String fileName);
}
