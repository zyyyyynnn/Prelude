package com.prelude.resume.api.port;

public interface ResumeContextPort {

    ResumeProjection requireOwnedProjection(Long userId, Long resumeId);
}
