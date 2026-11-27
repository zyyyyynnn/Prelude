package com.prelude.jobs.web;

import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.jobs.integration.BackgroundJobMessages.BackgroundJobView;
import com.prelude.jobs.integration.JobDispatcher;
import com.prelude.jobs.integration.JobQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQuery jobQuery;
    private final JobDispatcher jobDispatcher;
    private final CurrentAccount currentAccount;

    @GetMapping("/{jobId}")
    public Result<BackgroundJobView> get(@PathVariable String jobId) {
        return Result.success(jobQuery.view(jobId, currentAccount.requireId()));
    }

    @DeleteMapping("/{jobId}")
    public Result<BackgroundJobView> cancel(@PathVariable String jobId) {
        return Result.success(jobDispatcher.cancel(jobId, currentAccount.requireId()));
    }
}
