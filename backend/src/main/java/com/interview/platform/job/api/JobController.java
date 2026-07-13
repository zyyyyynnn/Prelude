package com.interview.platform.job.api;

import com.interview.shared.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQueryService jobQueryService;

    @GetMapping("/{jobId}")
    public Result<JobStatusResponse> get(@PathVariable String jobId) {
        return Result.success(jobQueryService.requireOwned(jobId));
    }
}
