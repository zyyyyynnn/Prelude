package com.interview.platform.job.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.job.AsyncJob;
import com.interview.platform.job.infrastructure.AsyncJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final AsyncJobMapper asyncJobMapper;

    public JobStatusResponse requireOwned(String jobId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        AsyncJob job = asyncJobMapper.selectOne(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getJobId, jobId)
            .eq(AsyncJob::getUserId, userId)
            .last("LIMIT 1"));
        if (job == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return new JobStatusResponse(
            job.getJobId(),
            job.getType(),
            job.getSubjectId(),
            job.getStatus(),
            job.getAttempts() == null ? 0 : job.getAttempts(),
            job.getCreatedAt(),
            job.getFinishedAt()
        );
    }
}
