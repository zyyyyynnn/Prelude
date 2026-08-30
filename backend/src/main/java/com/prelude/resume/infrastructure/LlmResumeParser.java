package com.prelude.resume.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prelude.BusinessException;
import com.prelude.llm.ChatPort;
import com.prelude.llm.ChatRequest;
import com.prelude.llm.LlmPurpose;
import com.prelude.llm.PromptRegistry;
import com.prelude.llm.PromptIds;
import com.prelude.resume.application.port.ResumeParser;
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
    public ParsedResume parse(Long accountId, String rawText) {
        String systemPrompt = promptRegistry.load(PromptIds.RESUME_PARSE);
        String content = chatPort.complete(ChatRequest.currentUser(
            accountId,
            LlmPurpose.PARSE,
            PromptIds.RESUME_PARSE,
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "请从以下中文简历文本中提取技能列表和项目经历：\n" + rawText)
            )
        ));
        try {
            ParsePayload payload = objectMapper.readValue(stripFence(content), ParsePayload.class);
            List<ParsedProject> projects = payload.projects() == null ? List.of() : payload.projects().stream()
                .map(project -> new ParsedProject(project.name(), project.description()))
                .toList();
            return new ParsedResume(payload.skills(), projects);
        } catch (JacksonException exception) {
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
