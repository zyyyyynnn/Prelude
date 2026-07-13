package com.interview.platform.llm.api;

import com.interview.shared.api.Result;
import com.interview.platform.llm.api.LlmProviderResponse;
import com.interview.platform.llm.LlmConfigPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmConfigPort llmConfigPort;

    @GetMapping("/providers")
    public Result<List<LlmProviderResponse>> providers() {
        return Result.success(llmConfigPort.listProviders());
    }
}
