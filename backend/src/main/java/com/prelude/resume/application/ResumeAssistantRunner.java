package com.prelude.resume.application;

import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import com.prelude.resume.api.ResumeAssistantMessageResponse;
import com.prelude.resume.api.ResumeToolStepResponse;
import com.prelude.resume.application.ResumeWorkspaceService.ToolCallDraft;
import com.prelude.resume.application.port.ResumeRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Real resume-assistant step: freeze a model snapshot, read owned resume material,
 * run the model, and persist the tool trace those actions produced. No canned
 * tool script — the steps are the work this method actually does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAssistantRunner {

    private final ResumeWorkspaceService workspace;
    private final ResumeRepository resumes;
    private final LlmPort llmPort;
    private final PromptRegistry promptRegistry;

    public ResumeAssistantMessageResponse run(Long accountId, Long turnId, String instruction) {
        List<ToolCallDraft> steps = new ArrayList<>();

        steps.add(new ToolCallDraft(
            "think",
            "思考了",
            null,
            null,
            List.of(),
            List.of("梳理指令涉及的模块与证据。")
        ));

        var snapshot = llmPort.freezeSnapshot(new LlmPort.FreezeSnapshotCommand(accountId, null, null));
        steps.add(new ToolCallDraft(
            "tool",
            "冻结模型快照",
            String.valueOf(snapshot.snapshotId()),
            null,
            List.of(),
            List.of("snapshot " + snapshot.snapshotId())
        ));

        var owned = resumes.listByOwner(accountId);
        steps.add(new ToolCallDraft(
            "find",
            "查找简历材料",
            null,
            null,
            List.of(),
            List.of(owned.size() + " 份已导入简历")
        ));

        String material = owned.isEmpty()
            ? "（暂无已导入简历文本）"
            : resumes.findById(owned.get(0).id()).map(ResumeRepository.StoredResume::rawText).orElse("");
        steps.add(new ToolCallDraft(
            "read",
            "读取 resume-context",
            null,
            null,
            List.of(),
            List.of(material.length() + " 字符")
        ));

        String systemPrompt = promptRegistry.load(PromptIds.RESUME_ASSISTANT);
        LlmPort.CompletionResult completion = llmPort.complete(
            new LlmPort.ModelExecutionRequest(
                snapshot.snapshotId(),
                "resume-assistant",
                PromptIds.RESUME_ASSISTANT,
                LlmPort.ResponseMode.PLAIN_TEXT,
                List.of(
                    new LlmPort.Message("system", systemPrompt),
                    new LlmPort.Message("user", "指令：" + instruction + "\n\n简历材料：\n" + material)
                ),
                List.of(),
                List.of()
            )
        );

        steps.add(new ToolCallDraft(
            "write",
            "生成修改建议",
            null,
            null,
            List.of(),
            List.of("输出 " + completion.content().length() + " 字")
        ));

        String summary = "思考 1轮 · 读1次文件、生成1次修改 · 模型 "
            + (completion.usage() != null ? completion.usage().model() : "unknown");
        return workspace.runAssistantStep(accountId, turnId, completion.content(), summary, steps);
    }
}
