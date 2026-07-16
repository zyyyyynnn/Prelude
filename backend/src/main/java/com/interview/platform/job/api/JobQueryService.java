package com.interview.platform.job.api;

import com.interview.platform.job.JobQueryPort;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobQueryPort jobQueryPort;

    public JobStatusResponse requireOwned(String jobId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        JobQueryPort.JobSnapshot job = jobQueryPort.findOwned(jobId, userId)
            .orElseThrow(() -> new BusinessException(404, "任务不存在"));
        return new JobStatusResponse(
            job.jobId(),
            job.type(),
            job.subjectId(),
            job.status(),
            job.attempts(),
            job.createdAt(),
            job.finishedAt()
        );
    }
}
