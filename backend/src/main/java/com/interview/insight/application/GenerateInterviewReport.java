package com.interview.insight.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.web.UserContext;
import com.interview.insight.domain.InterviewReportDraft;
import com.interview.insight.domain.StructuredInterviewReport;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;
import com.interview.platform.llm.LlmSelection;
import com.interview.interview.api.port.InterviewReportPort;
import com.interview.insight.application.port.InsightRepository;
import com.interview.insight.domain.InterviewReportAssembler;
import com.interview.insight.domain.ReportParser;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.LlmPurpose;
import com.interview.platform.llm.PromptVersions;
import com.interview.platform.realtime.RealtimePort;
import com.interview.insight.application.port.InsightFixturePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateInterviewReport {

    private static final String STATUS_GENERATING = "generating";

    private final ObjectMapper objectMapper;
    private final InterviewReportPort interviewReportPort;
    private final InsightRepository insightRepository;
    private final ChatPort chatPort;
    private final InsightFixturePort devFixtureService;
    private final ReportParser interviewReportParser;
    private final InterviewReportAssembler interviewReportAssembler;
    private final RealtimePort realtimePort;

    public Outcome execute(Long sessionId, Long userId) {
        try {
            UserContext.setCurrentUserId(userId);
            UserContext.setCurrentSessionId(sessionId);

            log.info("Processing report generation for session {} and user {}", sessionId, userId);
            InterviewSession session = interviewReportPort.findSession(sessionId);
            if (session == null) {
                log.warn("Session {} not found, skipping", sessionId);
                return Outcome.SKIPPED;
            }
            if (!STATUS_GENERATING.equals(session.getStatus())) {
                log.info("Session {} status is '{}', expected '{}' — skipping duplicate or stale job",
                    sessionId, session.getStatus(), STATUS_GENERATING);
                return Outcome.SKIPPED;
            }

            List<InterviewMessage> messages = interviewReportPort.listMessages(sessionId);

            String prompt = buildFinishPrompt(session, messages);
            String reportContent;

            boolean devFixtureEnabled = devFixtureService != null && devFixtureService.isEnabled();
            if (devFixtureEnabled) {
                reportContent = devFixtureService.resolveReport(session.getTargetPosition());
                try {
                    Thread.sleep(1500); // Simulate processing delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Report generation interrupted", e);
                }
            } else {
                reportContent = chatPort.complete(ChatRequest.snapshot(
                    session.getUserId(),
                    LlmPurpose.REPORT,
                    PromptVersions.REPORT,
                    PromptVersions.V1,
                    List.of(
                        Map.of("role", "system", "content", """
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
                              "reportMarkdown": "完整 Markdown 兼容报告"
                            }
                            三个评分必须使用 1-10 整数范围。
                            """),
                        Map.of("role", "user", "content", prompt)
                    ),
                    new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
                    Map.of("response_format", Map.of("type", "json_object"))
                ));
            }

            InterviewReportDraft reportDraft = interviewReportParser.parseDraft(reportContent);
            interviewReportPort.closeCurrentStage(sessionId);
            persistScoreHistory(session, reportDraft);
            persistWeaknesses(session, reportDraft.reportMarkdown());

            List<InterviewStage> stages = interviewReportPort.listStages(sessionId);
            List<UserWeakness> weaknesses = insightRepository.listWeaknessesBySession(sessionId);
            StructuredInterviewReport structuredReport = interviewReportAssembler.assemble(
                reportDraft, stages, messages, weaknesses
            );
            String reportJson = objectMapper.writeValueAsString(structuredReport);

            interviewReportPort.completeReport(sessionId, reportJson);

            // Broadcast report ready event
            realtimePort.publish(sessionId, "report_ready", reportJson);
            log.info("Successfully finished report generation and broadcasted for session {}", sessionId);
            return Outcome.COMPLETED;
        } catch (Exception e) {
            throw new ReportGenerationException(sessionId, e);
        } finally {
            UserContext.remove();
        }
    }

    public void handleTerminalFailure(Long sessionId, Throwable error) {
        log.error("Failed to generate report for session {}", sessionId, error);
        realtimePort.publish(sessionId, "error", "报告生成失败: " + error.getMessage());
        try {
            interviewReportPort.restoreOngoing(sessionId);
        } catch (Exception restoreException) {
            log.error("Failed to restore session status for session {}", sessionId, restoreException);
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

    private void persistScoreHistory(InterviewSession session, InterviewReportDraft report) {
        try {
            ScoreHistory score = new ScoreHistory();
            score.setUserId(session.getUserId());
            score.setSessionId(session.getId());
            score.setTechnicalScore(report.scores().technical());
            score.setExpressionScore(report.scores().expression());
            score.setLogicScore(report.scores().logic());

            insightRepository.replaceScore(score);
        } catch (Exception exception) {
            log.warn("Failed to persist score history for session {}", session.getId(), exception);
        }
    }

    private void persistWeaknesses(InterviewSession session, String report) {
        try {
            List<UserWeakness> weaknesses;
            boolean devFixtureEnabled = devFixtureService != null && devFixtureService.isEnabled();
            if (devFixtureEnabled) {
                weaknesses = devFixtureService.buildWeaknesses(session.getUserId(), session.getId());
            } else {
                weaknesses = extractWeaknesses(session, report);
            }
            insightRepository.replaceWeaknesses(session.getId(), weaknesses);
        } catch (Exception exception) {
            log.warn("Failed to persist weaknesses for session {}", session.getId(), exception);
        }
    }

    private List<UserWeakness> extractWeaknesses(InterviewSession session, String report) throws Exception {
        String content = chatPort.complete(ChatRequest.snapshot(
            session.getUserId(),
            LlmPurpose.REPORT,
            PromptVersions.REPORT,
            PromptVersions.V1,
            List.of(
                Map.of("role", "system", "content", """
                    你是面试分析助手。请只输出严格 JSON 数组，不要输出 Markdown。
                    每个元素必须包含 category 和 description 两个字段。
                    示例：[{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
                    """),
                Map.of("role", "user", "content", "请从以下面试报告中提取 1 到 5 个候选人的薄弱点：\n" + report)
            ),
            new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
            null
        ));
        String json = stripJsonFence(content);
        List<WeaknessExtractionItem> items = objectMapper.readValue(json, new TypeReference<>() {});
        ArrayList<UserWeakness> weaknesses = new ArrayList<>();
        for (WeaknessExtractionItem item : items) {
            if (item.category() == null || item.category().isBlank() || item.description() == null || item.description().isBlank()) {
                continue;
            }
            UserWeakness weakness = new UserWeakness();
            weakness.setUserId(session.getUserId());
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
        COMPLETED,
        SKIPPED
    }

    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(Long sessionId, Throwable cause) {
            super("Report generation failed for session " + sessionId + ": " + cause.getMessage(), cause);
        }
    }
}
