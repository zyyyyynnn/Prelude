package com.interview.platform.llm;

import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ClasspathPromptRegistry implements PromptRegistry {

    private static final String SAFE_SEGMENT = "[a-zA-Z0-9._-]+";

    private final ResourceLoader resourceLoader;
    private final Map<PromptKey, String> cache = new ConcurrentHashMap<>();

    @Override
    public String load(String promptId, String version) {
        if (promptId == null || !promptId.matches(SAFE_SEGMENT)
            || version == null || !version.matches(SAFE_SEGMENT)) {
            throw BusinessException.badRequest("Prompt 标识或版本不合法");
        }
        return cache.computeIfAbsent(new PromptKey(promptId, version), this::readPrompt);
    }

    private String readPrompt(PromptKey key) {
        Resource resource = resourceLoader.getResource(
            "classpath:prompts/" + key.promptId() + "/" + key.version() + ".md"
        );
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw BusinessException.badRequest(
                "Prompt 不存在: " + key.promptId() + "@" + key.version()
            );
        }
    }

    private record PromptKey(String promptId, String version) {
    }
}
