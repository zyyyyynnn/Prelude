package com.prelude.jobs.api;

import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
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

    private final BackgroundJobOperations backgroundJobOperations;
    private final CurrentAccount currentAccount;

    @GetMapping("/{jobId}")
    public Result<BackgroundJobView> get(@PathVariable String jobId) {
        return Result.success(backgroundJobOperations.view(jobId, currentAccount.requireId()));
    }

    @DeleteMapping("/{jobId}")
    public Result<BackgroundJobView> cancel(@PathVariable String jobId) {
        return Result.success(backgroundJobOperations.cancel(jobId, currentAccount.requireId()));
    }
}
