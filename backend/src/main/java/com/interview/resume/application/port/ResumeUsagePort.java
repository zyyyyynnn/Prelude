package com.interview.resume.application.port;

import java.util.List;
import java.util.Map;

public interface ResumeUsagePort {

    Map<Long, Long> countSessions(List<Long> resumeIds);

    boolean isUsed(Long resumeId);
}
