package com.prelude.artifact.application;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.artifact.domain.InterviewReportDraft;
import com.prelude.artifact.domain.StructuredInterviewReport;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.artifact.domain.InterviewReportAssembler;
import com.prelude.artifact.domain.ReportParser;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateInterviewReport {

    private static final String STATUS_GENERATING = "generating";

    private final ObjectMapper objectMapper;
    private final InterviewReportPort interviewReportPort;
    private final LlmPort llmPort;
    private final ReportParser interviewReportParser;
    private final InterviewReportAssembler interviewReportAssembler;

    public GenerationResult execute(Long sessionId, Long accountId) {
        try {

            log.info("Processing report generation for session {} and account {}", sessionId, accountId);
            InterviewSession session = interviewReportPort.findSession(sessionId);
            if (session == null) {
                throw new IllegalStateException("Interview session does not exist: " + sessionId);
            }
            if (!STATUS_GENERATING.equals(session.getStatus())) {
                if ("finished".equals(session.getStatus())
                    && session.getSummaryReport() != null && !session.getSummaryReport().isBlank()) {
                    log.info("Session {} already has a completed report; treating delivery as idempotent", sessionId);
                    return new GenerationResult(
                        Outcome.SKIPPED, session.getSummaryReport(), null, List.of());
                }
                throw new IllegalStateException(
                    "Report job requires a generating interview session; session=" + sessionId
                        + ", status=" + session.getStatus());
            }

            List<InterviewMessage> messages = interviewReportPort.listMessages(sessionId);
            String prompt = buildFinishPrompt(session, messages);
            LlmPort.CompletionResult reportCompletion = llmPort.complete(
                new LlmPort.ModelExecutionRequest(
                    session.getModelExecutionSnapshotId(),
                    "report",
                    PromptIds.REPORT,
                    LlmPort.ResponseMode.JSON_OBJECT,
                    List.of(
                        new LlmPort.Message("system", """
                                你是严谨的面试评估助手。请只输出严格 JSON，不要输出 Markdown 代码围栏。
                                JSON Schema（不得增加 overall、stage score、question score 或 weaknesses）：
                                {
                                  "summary": {
                                    "fitAssessment": "岗位适配判断",
                                    "actionRecommendation": "继续投递或专项训练建议",
                                    "overallRisk": "总体风险"
                                  },
                                  "scores": {
                                    "technical": 1-10 的整数,
                                    "expression": 1-10 的整数,
                                    "logic": 1-10 的整数
                                  },
                                  "stagePerformances": [{
                                    "stageName": "warmup|technical|deep_dive|closing",
                                    "summary": "阶段总结",
                                    "positiveSignals": ["正向信号"],
                                    "negativeSignals": ["风险信号"],
                                    "improvementSuggestions": ["改进建议"]
                                  }],
                                  "strengths": ["核心优势"],
                                  "trainingPlan": {
                                    "threeDay": ["3 天补强"],
                                    "sevenDay": ["7 天专项"],
                                    "nextInterviewFocus": ["下次模拟重点"]
                                  },
                                  "finalAdvice": "总结建议",
                                  "reportMarkdown": "完整 Markdown 报告"
                                }
                            三个评分必须使用 1-10 整数范围。
                            """),
                        new LlmPort.Message("user", prompt)
                    ),
                    List.of(),
                    List.of()
                ));

            InterviewReportDraft reportDraft = interviewReportParser.parseDraft(reportCompletion.content());
            ScoreHistory scoreHistory = scoreHistory(session, reportDraft);
            List<AccountWeakness> weaknesses = extractWeaknessesBestEffort(session, reportDraft.reportMarkdown());
            List<InterviewStage> stages = interviewReportPort.listStages(sessionId);
            StructuredInterviewReport structuredReport = interviewReportAssembler.assemble(
                reportDraft, stages, messages, weaknesses
            );
            String reportJson = objectMapper.writeValueAsString(structuredReport);

            log.info("Successfully prepared report generation for session {}", sessionId);
            return new GenerationResult(Outcome.GENERATED, reportJson, scoreHistory, weaknesses);
        } catch (Exception e) {
            throw new ReportGenerationException(sessionId, e);
        }
    }

    private String buildFinishPrompt(InterviewSession session, List<InterviewMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据以下模拟面试记录生成结构化 JSON 评估结果。目标岗位：")
            .append(session.getTargetPosition())
            .append("""

                reportMarkdown 字段中的 Markdown 报告必须包含以下固定字段：
                技术能力：X/10
                表达清晰度：X/10
                逻辑思维：X/10

                并继续输出以下内容：
                1. 三维评分解释
                2. 核心优势总结
                3. 改进建议（3条）
                4. 总结结论

                面试记录：
                """);
        for (InterviewMessage message : messages) {
            if (!"system".equals(message.getRole())) {
                builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        return builder.toString();
    }

    private ScoreHistory scoreHistory(InterviewSession session, InterviewReportDraft report) {
        ScoreHistory score = new ScoreHistory();
        score.setAccountId(session.getAccountId());
        score.setSessionId(session.getId());
        score.setTechnicalScore(report.scores().technical());
        score.setExpressionScore(report.scores().expression());
        score.setLogicScore(report.scores().logic());
        return score;
    }

    private List<AccountWeakness> extractWeaknessesBestEffort(InterviewSession session, String report) {
        try {
            return extractWeaknesses(session, report);
        } catch (Exception exception) {
            log.warn("Failed to extract weaknesses for session {}", session.getId(), exception);
            return List.of();
        }
    }

    private List<AccountWeakness> extractWeaknesses(InterviewSession session, String report) throws Exception {
        LlmPort.CompletionResult weaknessCompletion = llmPort.complete(
            new LlmPort.ModelExecutionRequest(
                session.getModelExecutionSnapshotId(),
                "weaknesses",
                PromptIds.REPORT,
                LlmPort.ResponseMode.JSON_ARRAY,
                List.of(
                    new LlmPort.Message("system", """
                        你是面试分析助手。请只输出严格 JSON 数组，不要输出 Markdown。
                        每个元素必须包含 category 和 description 两个字段。
                        示例：[{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
                        """),
                    new LlmPort.Message("user", "请从以下面试报告中提取 1 到 5 个候选人的薄弱点：\n" + report)
                ),
                List.of(),
                List.of()
            ));
        String json = stripJsonFence(weaknessCompletion.content());
        List<WeaknessExtractionItem> items = objectMapper.readValue(json, new TypeReference<>() {});
        ArrayList<AccountWeakness> weaknesses = new ArrayList<>();
        for (WeaknessExtractionItem item : items) {
            if (item.category() == null || item.category().isBlank() || item.description() == null || item.description().isBlank()) {
                continue;
            }
            AccountWeakness weakness = new AccountWeakness();
            weakness.setAccountId(session.getAccountId());
            weakness.setSessionId(session.getId());
            weakness.setCategory(item.category().trim());
            weakness.setDescription(item.description().trim());
            weaknesses.add(weakness);
        }
        return weaknesses;
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            int start = trimmed.indexOf('[');
            int end   = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private record WeaknessExtractionItem(String category, String description) {}

    public enum Outcome {
        GENERATED,
        SKIPPED
    }

    public record GenerationResult(
        Outcome outcome,
        String reportJson,
        ScoreHistory scoreHistory,
        List<AccountWeakness> weaknesses
    ) {
        public GenerationResult {
            weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
        }
    }

    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(Long sessionId, Throwable cause) {
            super("Report generation failed for session " + sessionId + ": " + cause.getMessage(), cause);
        }
    }
}
