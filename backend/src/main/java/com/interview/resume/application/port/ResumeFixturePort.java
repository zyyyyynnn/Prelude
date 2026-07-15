package com.interview.resume.application.port;

import com.interview.resume.application.ImportResumeResult;

public interface ResumeFixturePort {

    boolean isEnabled();

    ImportResumeResult createDevFixtureResume(Long userId, String fileName);
}
