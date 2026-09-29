package com.prelude.resume.application;

import com.prelude.resume.domain.BlockOperation;
import com.prelude.resume.domain.PatchCandidate;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The gate between a model's answer and anything a candidate can act on.
 *
 * <p>It checks the patch against the document it claims to edit — addressed blocks must
 * exist, verbs must be real, text must fit — and separately reports numbers the candidate's
 * own material does not support. The second part is deliberately not a rejection: a
 * candidate who knows their latency figure is real should be able to accept it, and the
 * workspace has to show which claim that was.
 */
@Component
public class ResumePolicy {

    /** A quantity with its unit, which is the kind of claim a resume gets inflated with. */
    private static final Pattern QUANTIFIED_CLAIM = Pattern.compile(
        "\\d+(?:[.,]\\d+)?\\s*(?:%|％|ms|毫秒|秒|分钟|小时|天|周|月|年|万人|千人|人|次|个|项|倍|k|K|w|万|亿|QPS|TPS|分)");

    /**
     * A candidate that passed, or the list of things to say back to the model.
     *
     * @param operations the patch in its stored form
     * @param affectedBlockIds blocks the patch touches
     * @param factRisk claims the material does not support
     * @param problems why the candidate was refused
     */
    public record Verdict(
        List<BlockOperation> operations,
        List<String> affectedBlockIds,
        List<ResumePatchProposal.FactRisk> factRisk,
        List<String> problems
    ) {

        public boolean valid() {
            return problems.isEmpty();
        }
    }

    public Verdict evaluate(ResumeDocument base, PatchCandidate candidate, String material) {
        List<String> problems = new ArrayList<>();
        List<BlockOperation> operations = new ArrayList<>();
        Set<String> affected = new LinkedHashSet<>();
        List<ResumePatchProposal.FactRisk> risks = new ArrayList<>();

        if (candidate.operations().isEmpty()) {
            problems.add("候选没有提出任何块级操作。");
        }
        if (candidate.operations().size() > PatchCandidate.MAX_OPERATIONS) {
            problems.add("候选操作过多，最多 " + PatchCandidate.MAX_OPERATIONS + " 条。");
        }
        if (candidate.reason() == null || candidate.reason().isBlank()) {
            problems.add("候选缺少修改理由。");
        }

        Set<String> sections = new LinkedHashSet<>();
        for (ResumeDocument.Block block : base.blocks()) {
            sections.add(block.section());
        }

        for (PatchCandidate.Operation operation : candidate.operations()) {
            BlockOperation.Kind kind = verb(operation.op());
            if (kind == null) {
                problems.add("未知的操作类型：" + operation.op());
                continue;
            }
            if (kind != BlockOperation.Kind.INSERT
                && (operation.blockId() == null || operation.blockId().isBlank())) {
                problems.add("操作必须指名块 id。");
                continue;
            }
            if (kind == BlockOperation.Kind.INSERT) {
                if (operation.section() == null || !sections.contains(operation.section())) {
                    problems.add("新增块指向了文档中不存在的分区：" + operation.section());
                    continue;
                }
            } else if (base.block(operation.blockId()).isEmpty()) {
                problems.add("操作引用了文档中不存在的块：" + operation.blockId());
                continue;
            }
            if (kind != BlockOperation.Kind.DELETE) {
                String text = operation.text();
                if (text == null || text.isBlank()) {
                    problems.add("块 " + operation.blockId() + " 的新内容为空。");
                    continue;
                }
                if (text.length() > PatchCandidate.MAX_TEXT_LENGTH) {
                    problems.add("块 " + operation.blockId() + " 的新内容过长。");
                    continue;
                }
                if (kind != BlockOperation.Kind.INSERT) {
                    base.block(operation.blockId())
                        .filter(before -> before.text().equals(text))
                        .ifPresent(before -> problems.add(
                            "块 " + before.id() + " 的内容与原文完全相同，无需提交。"));
                }
                risks.addAll(unsupportedClaims(operation.blockId(), text, material));
            }
            operations.add(new BlockOperation(kind, operation.blockId(), operation.section(), operation.text()));
            if (operation.blockId() != null) {
                affected.add(operation.blockId());
            }
        }

        return new Verdict(
            operations,
            List.copyOf(affected),
            risks,
            List.copyOf(new LinkedHashSet<>(problems)));
    }

    private static BlockOperation.Kind verb(String spelling) {
        if (spelling == null) {
            return null;
        }
        return switch (spelling.trim().toLowerCase(Locale.ROOT)) {
            case "replace" -> BlockOperation.Kind.REPLACE;
            case "insert" -> BlockOperation.Kind.INSERT;
            case "delete" -> BlockOperation.Kind.DELETE;
            default -> null;
        };
    }

    /**
     * A number in the new text is a claim only when the material the candidate supplied
     * does not already carry it. Repeated claims are reported once per block.
     */
    private static List<ResumePatchProposal.FactRisk> unsupportedClaims(
        String blockId,
        String text,
        String material
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String source = material == null ? "" : material.replaceAll("\\s+", "");
        List<ResumePatchProposal.FactRisk> risks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = QUANTIFIED_CLAIM.matcher(text);
        while (matcher.find()) {
            String claim = matcher.group().trim();
            String normalised = claim.replaceAll("\\s+", "");
            if (!source.contains(normalised) && seen.add(claim)) {
                risks.add(new ResumePatchProposal.FactRisk(blockId, claim));
            }
        }
        return risks;
    }
}
