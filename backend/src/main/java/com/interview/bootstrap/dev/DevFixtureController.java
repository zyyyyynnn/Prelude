package com.interview.bootstrap.dev;

import com.interview.shared.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev-fixtures")
@ConditionalOnProperty(prefix = "app.dev-fixtures", name = "enabled", havingValue = "true")
public class DevFixtureController {

    private final DevFixtureService devFixtureService;

    @PostMapping("/reset")
    public Result<Void> reset() {
        devFixtureService.reset();
        return Result.success();
    }
}
