package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.LlmProviderResponse;
import com.interview.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmRouter llmRouter;

    @GetMapping("/providers")
    public Result<List<LlmProviderResponse>> providers() {
        return Result.success(llmRouter.listEnabledProviders());
    }
}
