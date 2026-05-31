package com.interview.controller;

import com.interview.common.Result;
import com.interview.service.DemoModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo")
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoController {

    private final DemoModeService demoModeService;

    @PostMapping("/reset")
    public Result<Void> reset() {
        demoModeService.reset();
        return Result.success();
    }
}
