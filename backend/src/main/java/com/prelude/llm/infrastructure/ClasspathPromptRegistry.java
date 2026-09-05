package com.prelude.llm.infrastructure;

import com.prelude.llm.api.PromptRegistry;

import com.prelude.BusinessException;
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
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String load(String promptId) {
        if (promptId == null || !promptId.matches(SAFE_SEGMENT)) {
            throw BusinessException.badRequest("Prompt 标识不合法");
        }
        return cache.computeIfAbsent(promptId, this::readPrompt);
    }

    private String readPrompt(String promptId) {
        Resource resource = resourceLoader.getResource(
            "classpath:prompts/" + promptId + ".md"
        );
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw BusinessException.badRequest("Prompt 不存在: " + promptId);
        }
    }
}
