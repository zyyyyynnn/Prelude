package com.prelude.resume.application.port;

import com.prelude.resume.application.ImportResumeResult;

public interface ResumeFixturePort {

    boolean isEnabled();

    ImportResumeResult createDevFixtureResume(Long userId, String fileName);
}
