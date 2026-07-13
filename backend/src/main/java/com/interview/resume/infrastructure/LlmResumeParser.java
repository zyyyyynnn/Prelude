package com.interview.resume.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.LlmPurpose;
import com.interview.platform.llm.PromptRegistry;
import com.interview.platform.llm.PromptVersions;
import com.interview.resume.application.port.ResumeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmResumeParser implements ResumeParser {

    private final ChatPort chatPort;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public ParsedResume parse(Long userId, String rawText) {
        String systemPrompt = promptRegistry.load(PromptVersions.RESUME_PARSE, PromptVersions.V1);
        String content = chatPort.complete(new ChatRequest(
            userId,
            LlmPurpose.PARSE,
            PromptVersions.RESUME_PARSE,
            PromptVersions.V1,
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "请从以下中文简历文本中提取技能列表和项目经历：\n" + rawText)
            ),
            null,
            null,
            null,
            null
        ));
        try {
            ParsePayload payload = objectMapper.readValue(stripFence(content), ParsePayload.class);
            List<ParsedProject> projects = payload.projects() == null ? List.of() : payload.projects().stream()
                .map(project -> new ParsedProject(project.name(), project.description()))
                .toList();
            return new ParsedResume(payload.skills(), projects);
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("LLM 返回内容不是合法 JSON，请重试");
        }
    }

    private String stripFence(String content) {
        if (content == null) {
            return "";
        }
        String value = content.trim();
        if (value.startsWith("```json")) {
            value = value.substring(7);
        } else if (value.startsWith("```")) {
            value = value.substring(3);
        }
        if (value.endsWith("```")) {
            value = value.substring(0, value.length() - 3);
        }
        return value.trim();
    }

    private record ParsePayload(List<String> skills, List<ProjectPayload> projects) {
    }

    private record ProjectPayload(String name, String description) {
    }
}
