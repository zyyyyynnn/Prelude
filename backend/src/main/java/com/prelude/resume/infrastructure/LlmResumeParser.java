package com.prelude.resume.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prelude.BusinessException;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptRegistry;
import com.prelude.llm.api.PromptIds;
import com.prelude.resume.application.port.ResumeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmResumeParser implements ResumeParser {

    private final LlmPort llmPort;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public ParsedResume parse(Long accountId, String rawText) {
        String systemPrompt = promptRegistry.load(PromptIds.RESUME_PARSE);
        var snapshotRef = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null));
        LlmPort.CompletionResult completion = llmPort.complete(
            new LlmPort.ModelExecutionRequest(
                snapshotRef.snapshotId(),
                "resume-parse",
                PromptIds.RESUME_PARSE,
                LlmPort.ResponseMode.JSON,
                List.of(
                    new LlmPort.Message("system", systemPrompt),
                    new LlmPort.Message("user", "请从以下中文简历文本中提取技能列表和项目经历：\n" + rawText)
                ),
                List.of(),
                List.of()
            ));
        String content = completion.content();
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
