package com.prelude.jobs.api;

import com.prelude.jobs.JobQueryPort;
import com.prelude.BusinessException;
import com.prelude.UserContext;
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
