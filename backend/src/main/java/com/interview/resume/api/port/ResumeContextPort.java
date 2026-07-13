package com.interview.resume.api.port;

public interface ResumeContextPort {

    ResumeProjection requireOwnedProjection(Long userId, Long resumeId);
}
