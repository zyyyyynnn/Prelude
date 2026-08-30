package com.prelude.jobs.api;

import com.prelude.jobs.JobQueryPort;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobQueryPort jobQueryPort;
    private final CurrentAccount currentAccount;

    public JobStatusResponse requireOwned(String jobId) {
        long accountId = currentAccount.requireId();
        JobQueryPort.JobSnapshot job = jobQueryPort.findOwned(jobId, accountId)
            .orElseThrow(() -> BusinessException.notFound("任务不存在"));
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
