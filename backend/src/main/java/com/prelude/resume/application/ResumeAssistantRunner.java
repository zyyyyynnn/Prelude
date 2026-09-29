package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.PatchCandidate;
import com.prelude.resume.domain.ResumeAgentRun;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumeConversation;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.domain.ResumeRevision;
import com.prelude.resume.domain.ResumeTurn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One assistant run, end to end: freeze a model execution, hand the model real tools over
 * the candidate's own resume, let it decide which ones to call, and turn its answer into a
 * policy-checked patch proposal.
 *
 * <p>Every step the workspace shows is recorded inside the handler that ran, so a step
 * exists only because the model asked for that work. Nothing here narrates a fixed script.
 *
 * <p>No transaction spans the model call: steps commit as they happen so a waiting
 * candidate sees progress, and a run that dies mid-flight leaves the steps it really got to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAssistantRunner {

    /** Enough material to ground a claim without letting one import crowd out the document. */
    private static final int MAX_MATERIAL_CHARACTERS = 6000;

    private static final int MAX_SEARCH_HITS = 8;

    private final ResumeWorkspaceRepository workspace;
    private final ResumeTranscriptRepository transcript;
    private final ResumeDocumentRepository documents;
    private final ResumeRepository resumes;
    private final AttachmentContextPort attachments;
    private final LlmPort llmPort;
    private final PromptRegistry promptRegistry;
    private final ResumePolicy policy;
    private final ObjectMapper mapper;

    /**
     * Run one already-claimed turn. Anything that escapes is reported after the run is
     * closed, so the turn lands {@code failed} with a reason instead of staying running.
     */
    public void run(Long accountId, ResumeTurn turn) {
        ResumeConversation conversation = workspace
            .findConversation(accountId, turn.conversationId())
            .orElseThrow(() -> BusinessException.notFound("会话不存在"));
        Long resumeId = resolveResume(accountId, conversation);

        ResumeRevision base = documents.latest(resumeId).orElseGet(() -> seed(accountId, resumeId));
        ModelExecutionSnapshotRef snapshot = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null));

        Long runId = transcript.openRun(new ResumeAgentRun(
            null, turn.id(), conversation.id(), resumeId, base.revisionNumber(),
            snapshot.snapshotId(), PromptIds.RESUME_ASSISTANT,
            ResumeAgentRun.Status.RUNNING, null, LocalDateTime.now(), null));
        Trace trace = new Trace(runId);

        try {
            String material = material(accountId, resumeId, turn);
            List<LlmPort.ToolBinding> tools = tools(trace, base.document(), material);
            trace.record(ResumeAgentStep.Kind.THINK,
                "梳理指令与文档结构", List.of(), List.of(
                    "基线版本 r" + base.revisionNumber(),
                    "文档块 " + base.document().blocks().size() + " 个"),
                List.of(), ResumeAgentStep.State.DONE, null, null, null);

            String systemPrompt = promptRegistry.load(PromptIds.RESUME_ASSISTANT);
            String userPrompt = userPrompt(turn, base.document(), material);

            PatchCandidate candidate = ask(trace, snapshot.snapshotId(), systemPrompt, userPrompt, tools);
            ResumePolicy.Verdict verdict = policy.evaluate(base.document(), candidate, material);
            if (!verdict.valid()) {
                trace.record(ResumeAgentStep.Kind.POLICY, "校验补丁", List.of(), verdict.problems(),
                    List.of(), ResumeAgentStep.State.ERROR, String.join("；", verdict.problems()),
                    null, null);
                candidate = ask(trace, snapshot.snapshotId(), systemPrompt,
                    userPrompt + "\n\n上一版补丁被拒绝，原因：\n- "
                        + String.join("\n- ", verdict.problems())
                        + "\n本回合只允许再修正一次，请重新只输出 JSON 对象。",
                    tools);
                verdict = policy.evaluate(base.document(), candidate, material);
            }

            if (!verdict.valid()) {
                trace.record(ResumeAgentStep.Kind.POLICY, "校验补丁", List.of(), verdict.problems(),
                    List.of(), ResumeAgentStep.State.ERROR, String.join("；", verdict.problems()),
                    "已拒绝", ResumeAgentStep.BadgeTone.ERROR);
                transcript.finishRun(runId, ResumeAgentRun.Status.INVALID,
                    String.join("；", verdict.problems()), LocalDateTime.now());
                transcript.appendMessage(turn.id(), conversation.id(),
                    "这次修改没有通过校验，简历未被改动：" + String.join("；", verdict.problems()),
                    LocalDateTime.now());
                workspace.touchConversation(conversation.id(), LocalDateTime.now());
                return;
            }

            ResumeDocument.Applied projected = base.document().apply(verdict.operations());
            Long proposalId = transcript.recordProposal(new ResumePatchProposal(
                null, runId, resumeId, conversation.id(), base.revisionNumber(),
                projected.affectedBlockIds(), candidate.reason(), verdict.operations(),
                verdict.factRisk(), ResumePatchProposal.Status.PENDING, null, null, null,
                LocalDateTime.now()));

            trace.record(ResumeAgentStep.Kind.POLICY, "校验补丁",
                List.of(verdict.operations().size() + " 条操作"),
                verdict.factRisk().isEmpty()
                    ? List.of("未发现材料之外的数字")
                    : verdict.factRisk().stream().map(ResumePatchProposal.FactRisk::statement).toList(),
                List.of(), ResumeAgentStep.State.DONE, null,
                verdict.factRisk().isEmpty() ? null : "事实待核",
                verdict.factRisk().isEmpty() ? null : ResumeAgentStep.BadgeTone.ERROR);
            trace.record(ResumeAgentStep.Kind.PROPOSAL, "生成补丁提案",
                List.of("r" + base.revisionNumber() + " → 待确认"),
                List.of("提案 #" + proposalId,
                    "改动 " + projected.affectedBlockIds().size() + " 个块"),
                List.of(new ResumeAgentStep.FileDiff("resume.md",
                    projected.linesAdded(), projected.linesRemoved())),
                ResumeAgentStep.State.DONE, null, "待确认", ResumeAgentStep.BadgeTone.DEFAULT);

            transcript.finishRun(runId, ResumeAgentRun.Status.PROPOSAL_READY, null, LocalDateTime.now());
            transcript.appendMessage(turn.id(), conversation.id(), candidate.reason(), LocalDateTime.now());
            workspace.touchConversation(conversation.id(), LocalDateTime.now());
        } catch (RuntimeException error) {
            transcript.finishRun(runId, ResumeAgentRun.Status.FAILED, reasonOf(error), LocalDateTime.now());
            throw error;
        }
    }

    /**
     * The document a run patches. A conversation usually names one; when it does not, an
     * account holding a single resume is unambiguous and gets attached so later runs agree, while
     * an account with several is refused rather than guessed at — silently picking the newest
     * resume would edit a document nobody pointed at.
     */
    private Long resolveResume(Long accountId, ResumeConversation conversation) {
        if (conversation.resumeId() != null) {
            return conversation.resumeId();
        }
        List<ResumeRepository.ResumeListItem> owned = resumes.listByOwner(accountId);
        if (owned.isEmpty()) {
            throw BusinessException.badRequest("这个账号还没有导入可修改的简历");
        }
        if (owned.size() > 1) {
            throw BusinessException.badRequest("这个账号有多份简历，请先指明要修改哪一份");
        }
        Long resolved = owned.getFirst().id();
        workspace.attachResume(conversation.id(), resolved);
        return resolved;
    }

    /**
     * Ask for a candidate. An answer that is not the agreed JSON object is a transport
     * failure rather than a policy refusal, so it is retried once on its own terms.
     */
    private PatchCandidate ask(
        Trace trace,
        Long snapshotId,
        String systemPrompt,
        String userPrompt,
        List<LlmPort.ToolBinding> tools
    ) {
        String content = complete(snapshotId, systemPrompt, userPrompt, tools);
        try {
            return mapper.readValue(content, PatchCandidate.class);
        } catch (RuntimeException malformed) {
            trace.record(ResumeAgentStep.Kind.POLICY, "解析候选补丁", List.of(),
                List.of("模型返回不是约定的 JSON 对象"), List.of(),
                ResumeAgentStep.State.ERROR, "输出格式无效", null, null);
            return mapper.readValue(
                complete(snapshotId, systemPrompt,
                    userPrompt + "\n\n只输出 JSON 对象本身，不要任何解释或代码块。", tools),
                PatchCandidate.class);
        }
    }

    private String complete(
        Long snapshotId,
        String systemPrompt,
        String userPrompt,
        List<LlmPort.ToolBinding> tools
    ) {
        LlmPort.CompletionResult result = llmPort.complete(new LlmPort.ModelExecutionRequest(
            snapshotId,
            "resume-assistant",
            PromptIds.RESUME_ASSISTANT,
            LlmPort.ResponseMode.JSON_OBJECT,
            List.of(
                new LlmPort.Message("system", systemPrompt),
                new LlmPort.Message("user", userPrompt)),
            List.of(),
            tools));
        if (result == null || result.content() == null || result.content().isBlank()) {
            throw BusinessException.badRequest("模型未返回可用的补丁候选");
        }
        return result.content();
    }

    /**
     * The tools the model may call. Each handler records its own step when it runs, which is
     * what makes the trace a record of the model's decisions rather than of this method.
     */
    private List<LlmPort.ToolBinding> tools(Trace trace, ResumeDocument document, String material) {
        return List.of(
            new LlmPort.ToolBinding(
                "list_blocks",
                "列出简历文档的全部块：块 id、分区、类型与内容",
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",
                arguments -> {
                    String json = mapper.writeValueAsString(document.blocks());
                    trace.tool(ResumeAgentStep.Kind.READ, "list_blocks", "查看文档结构",
                        List.of(document.blocks().size() + " 块"),
                        document.blocks().stream().map(block -> block.id() + " · " + block.section()).toList(),
                        ResumeAgentStep.State.DONE, null, null, null);
                    return json;
                }),
            new LlmPort.ToolBinding(
                "read_block",
                "读取指定块的完整内容",
                "{\"type\":\"object\",\"properties\":{\"blockId\":{\"type\":\"string\"}},"
                    + "\"required\":[\"blockId\"]}",
                arguments -> {
                    String blockId = argument(arguments, "blockId");
                    return document.block(blockId)
                        .map(block -> {
                            trace.tool(ResumeAgentStep.Kind.READ, "read_block", "读取块 " + blockId,
                                List.of(block.section()), List.of(summarise(block.text())),
                                ResumeAgentStep.State.DONE, null, null, null);
                            return mapper.writeValueAsString(block);
                        })
                        .orElseGet(() -> {
                            trace.tool(ResumeAgentStep.Kind.READ, "read_block", "读取块 " + blockId,
                                List.of(), List.of("文档中没有这个块"),
                                ResumeAgentStep.State.ERROR, "块不存在", null, null);
                            return "{\"error\":\"block not found\"}";
                        });
                }),
            new LlmPort.ToolBinding(
                "search_material",
                "在候选人提供的原始材料中检索关键词，返回命中行，用于核对数字与经历",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                    + "\"required\":[\"query\"]}",
                arguments -> {
                    String query = argument(arguments, "query");
                    List<String> hits = search(query, material);
                    trace.tool(ResumeAgentStep.Kind.SEARCH, "search_material", "检索原始材料",
                        List.of(query), hits.isEmpty() ? List.of("无命中") : hits,
                        ResumeAgentStep.State.DONE, null,
                        hits.size() + " 命中", ResumeAgentStep.BadgeTone.DEFAULT);
                    return mapper.writeValueAsString(hits);
                }));
    }

    /** The candidate's own material: the imported resume text plus anything attached. */
    private String material(Long accountId, Long resumeId, ResumeTurn turn) {
        ResumeRepository.StoredResume stored = resumes.findById(resumeId)
            .filter(candidate -> candidate.accountId().equals(accountId))
            .orElseThrow(() -> BusinessException.notFound("简历不存在"));
        StringBuilder material = new StringBuilder(stored.rawText());
        if (!turn.attachmentIds().isEmpty()) {
            for (AttachmentSnapshot attachment : attachments.requireOwned(accountId, turn.attachmentIds())) {
                if (!attachment.image() && attachment.text() != null) {
                    material.append("\n\n").append(attachment.text());
                }
            }
            attachments.bind(accountId, turn.attachmentIds(), "resume", turn.conversationId());
        }
        return material.length() > MAX_MATERIAL_CHARACTERS
            ? material.substring(0, MAX_MATERIAL_CHARACTERS)
            : material.toString();
    }

    private String userPrompt(ResumeTurn turn, ResumeDocument document, String material) {
        return """
            指令：%s
            指令指向的块：%s

            当前文档块（JSON）：
            %s

            候选人原始材料，核对事实用：
            %s""".formatted(
            turn.instruction(),
            turn.blockIds().isEmpty() ? "整份简历" : String.join("、", turn.blockIds()),
            mapper.writeValueAsString(document.blocks()),
            material);
    }

    /**
     * The first revision of an imported resume: skills and projects become addressed blocks
     * once, and every later revision carries those same ids forward.
     */
    private ResumeRevision seed(Long accountId, Long resumeId) {
        ResumeRepository.StoredResume stored = resumes.findById(resumeId)
            .filter(candidate -> candidate.accountId().equals(accountId))
            .orElseThrow(() -> BusinessException.notFound("简历不存在"));

        List<ResumeDocument.Block> blocks = new ArrayList<>();
        blocks.add(new ResumeDocument.Block("概览-1", "概览", ResumeDocument.Block.SECTION, "概览"));
        blocks.add(new ResumeDocument.Block("概览-2", "概览", ResumeDocument.Block.ITEM,
            firstNonBlankLine(stored.rawText())));
        int skill = 1;
        for (String name : stored.parsedSkills()) {
            blocks.add(new ResumeDocument.Block("技能-" + skill++, "技能",
                ResumeDocument.Block.BULLET, name));
        }
        int project = 1;
        for (ResumeRepository.ParsedProject parsed : stored.parsedProjects()) {
            blocks.add(new ResumeDocument.Block("项目-" + project + "-标题", "项目",
                ResumeDocument.Block.ITEM, parsed.name()));
            blocks.add(new ResumeDocument.Block("项目-" + project++ + "-描述", "项目",
                ResumeDocument.Block.BULLET, parsed.description()));
        }
        return documents.append(new ResumeRevision(null, resumeId, 1,
            new ResumeDocument(blocks), "导入 PDF 解析结果",
            ResumeRevision.Origin.IMPORTED, null, LocalDateTime.now()));
    }

    private String argument(String argumentsJson, String field) {
        JsonNode node = mapper.readTree(
            argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        return node.path(field).asString("");
    }

    private static List<String> search(String query, String material) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return material.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && line.toLowerCase(Locale.ROOT).contains(needle))
            .limit(MAX_SEARCH_HITS)
            .toList();
    }

    private static String firstNonBlankLine(String text) {
        if (text == null) {
            return "";
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    private static String summarise(String text) {
        String line = firstNonBlankLine(text);
        return line.length() <= 60 ? line : line.substring(0, 60) + "…";
    }

    private static String reasonOf(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

    /** Appends steps to the open run, numbering them in the order they actually happened. */
    private final class Trace {

        private final Long runId;
        private final AtomicInteger order = new AtomicInteger();

        private Trace(Long runId) {
            this.runId = runId;
        }

        /** A step the run took on its own. */
        private void record(
            ResumeAgentStep.Kind kind,
            String label,
            List<String> chips,
            List<String> detail,
            List<ResumeAgentStep.FileDiff> files,
            ResumeAgentStep.State state,
            String error,
            String badge,
            ResumeAgentStep.BadgeTone badgeTone
        ) {
            append(kind, null, label, chips, detail, files, state, error, badge, badgeTone);
        }

        /** A step that exists because the model called this tool. */
        private void tool(
            ResumeAgentStep.Kind kind,
            String toolName,
            String label,
            List<String> chips,
            List<String> detail,
            ResumeAgentStep.State state,
            String error,
            String badge,
            ResumeAgentStep.BadgeTone badgeTone
        ) {
            append(kind, toolName, label, chips, detail, List.of(), state, error, badge, badgeTone);
        }

        private void append(
            ResumeAgentStep.Kind kind,
            String toolName,
            String label,
            List<String> chips,
            List<String> detail,
            List<ResumeAgentStep.FileDiff> files,
            ResumeAgentStep.State state,
            String error,
            String badge,
            ResumeAgentStep.BadgeTone badgeTone
        ) {
            transcript.appendStep(new ResumeAgentStep(
                null, runId, order.getAndIncrement(), kind, label, toolName, null, null,
                files, chips, detail, badge, badgeTone, state, error));
        }
    }
}
