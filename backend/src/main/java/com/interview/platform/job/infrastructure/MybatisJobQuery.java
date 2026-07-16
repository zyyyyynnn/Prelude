package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.job.JobQueryPort;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MybatisJobQuery implements JobQueryPort {

    private final AsyncJobMapper asyncJobMapper;

    @Override
    public Optional<JobSnapshot> findOwned(String jobId, Long userId) {
        AsyncJob job = asyncJobMapper.selectOne(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getJobId, jobId)
            .eq(AsyncJob::getUserId, userId)
            .last("LIMIT 1"));
        return Optional.ofNullable(job).map(this::toSnapshot);
    }

    private JobSnapshot toSnapshot(AsyncJob job) {
        return new JobSnapshot(
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
