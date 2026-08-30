package com.prelude.resume.api.port;

public interface ResumeContextPort {

    ResumeProjection requireOwnedProjection(Long accountId, Long resumeId);
}
