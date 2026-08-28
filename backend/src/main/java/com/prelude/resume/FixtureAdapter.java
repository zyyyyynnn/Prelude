package com.prelude.resume;

import com.prelude.resume.application.ImportResumeResult;
import com.prelude.resume.application.port.ResumeFixturePort;
import org.springframework.stereotype.Component;

@Component("resumeFixtureAdapter")
class FixtureAdapter implements ResumeFixturePort {
    @Override public boolean isEnabled() { return false; }
    @Override public ImportResumeResult createDevFixtureResume(Long userId, String fileName) { throw new UnsupportedOperationException(); }
}
