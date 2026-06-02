package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.UserContext;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.ScoreHistory;
import com.interview.entity.UserWeakness;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.service.DemoModeService;
import com.interview.llm.LlmRouter;
import com.interview.config.SseEmitterRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ReportJobWorker implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;
    private final LlmRouter llmRouter;
    private final DemoModeService demoModeService;
    private final InterviewReportParser interviewReportParser;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "report-job-worker");
        thread.setDaemon(true);
        return thread;
    });

    public ReportJobWorker(
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        InterviewSessionMapper interviewSessionMapper,
        InterviewMessageMapper interviewMessageMapper,
        InterviewStageMapper interviewStageMapper,
        ScoreHistoryMapper scoreHistoryMapper,
        UserWeaknessMapper userWeaknessMapper,
        LlmRouter llmRouter,
        DemoModeService demoModeService,
        InterviewReportParser interviewReportParser,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.interviewMessageMapper = interviewMessageMapper;
        this.interviewStageMapper = interviewStageMapper;
        this.scoreHistoryMapper = scoreHistoryMapper;
        this.userWeaknessMapper = userWeaknessMapper;
        this.llmRouter = llmRouter;
        this.demoModeService = demoModeService;
        this.interviewReportParser = interviewReportParser;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Override
    public void run(String... args) {
        executorService.submit(this::consumeJobs);
    }

    private void consumeJobs() {
        log.info("ReportJobWorker started consuming jobs from Redis queue:report:jobs");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String jobJson = stringRedisTemplate.opsForList().rightPop("queue:report:jobs", Duration.ofSeconds(5));
                if (jobJson == null) {
                    continue;
                }
                log.info("ReportJobWorker popped a job: {}", jobJson);
                ReportJob job = objectMapper.readValue(jobJson, ReportJob.class);
                processJob(job);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.error("Error processing report job in consumer loop", e);
            }
        }
    }

    private void processJob(ReportJob job) {
        Long sessionId = job.sessionId();
        Long userId = job.userId();

        try {
            UserContext.setCurrentUserId(userId);
            UserContext.setCurrentSessionId(sessionId);

            log.info("Processing report generation for session {} and user {}", sessionId, userId);
            InterviewSession session = interviewSessionMapper.selectById(sessionId);
            if (session == null) {
                log.warn("Session {} not found, skipping", sessionId);
                return;
            }

            List<InterviewMessage> messages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getSeqNum));

            String prompt = buildFinishPrompt(session, messages);
            String reportContent;

            boolean demoEnabled = demoModeService != null && demoModeService.isEnabled();
            if (demoEnabled) {
                reportContent = demoModeService.resolveReport(session.getTargetPosition());
                try {
                    Thread.sleep(1500); // Simulate processing delay
                } catch (InterruptedException ignored) {}
            } else {
                reportContent = llmRouter.chatWithSnapshot(
                    session.getLlmProvider(),
                    session.getLlmModel(),
                    List.of(
                        Map.of("role", "system", "content", """
                            你是严谨的面试评估助手。请只输出严格 JSON，不要输出 Markdown 代码围栏。
                            JSON Schema:
                            {
                              "reportMarkdown": "完整 Markdown 评估报告",
                              "scores": {
                                "technical": 1-10 的整数,
                                "expression": 1-10 的整数,
                                "logic": 1-10 的整数
                              }
                            }
                            三个评分必须使用 1-10 整数范围。
                            """),
                        Map.of("role", "user", "content", prompt)
                    ),
                    Map.of("response_format", Map.of("type", "json_object"))
                );
            }

            InterviewReportParser.ParsedReport parsedReport = interviewReportParser.parse(reportContent);
            String report = parsedReport.reportMarkdown();

            session.setStatus("finished");
            session.setSummaryReport(report);
            interviewSessionMapper.updateById(session);

            closeCurrentStage(sessionId);
            persistScoreHistory(session, parsedReport);
            persistWeaknesses(session, report);

            // Broadcast report ready event
            sseEmitterRegistry.broadcast(sessionId, "report_ready", report);
            log.info("Successfully finished report generation and broadcasted for session {}", sessionId);

        } catch (Exception e) {
            log.error("Failed to generate report for session {}", sessionId, e);
            sseEmitterRegistry.broadcast(sessionId, "error", "报告生成失败: " + e.getMessage());
            // Restore status to ongoing if failed
            try {
                InterviewSession session = interviewSessionMapper.selectById(sessionId);
                if (session != null && "generating".equals(session.getStatus())) {
                    session.setStatus("ongoing");
                    interviewSessionMapper.updateById(session);
                }
            } catch (Exception ex) {
                log.error("Failed to restore session status for session {}", sessionId, ex);
            }
        } finally {
            UserContext.remove();
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

    private void closeCurrentStage(Long sessionId) {
        InterviewStage stage = interviewStageMapper.selectOne(new LambdaQueryWrapper<InterviewStage>()
            .eq(InterviewStage::getSessionId, sessionId)
            .isNull(InterviewStage::getEndedAt)
            .orderByDesc(InterviewStage::getStartedAt)
            .last("LIMIT 1"));
        if (stage != null) {
            stage.setEndedAt(LocalDateTime.now());
            interviewStageMapper.updateById(stage);
        }
    }

    private void persistScoreHistory(InterviewSession session, InterviewReportParser.ParsedReport report) {
        try {
            ScoreHistory score = new ScoreHistory();
            score.setUserId(session.getUserId());
            score.setSessionId(session.getId());
            score.setTechnicalScore(report.technicalScore());
            score.setExpressionScore(report.expressionScore());
            score.setLogicScore(report.logicScore());

            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .eq(ScoreHistory::getSessionId, session.getId()));
            scoreHistoryMapper.insert(score);
        } catch (Exception exception) {
            log.warn("Failed to persist score history for session {}", session.getId(), exception);
        }
    }

    private void persistWeaknesses(InterviewSession session, String report) {
        try {
            List<UserWeakness> weaknesses;
            boolean demoEnabled = demoModeService != null && demoModeService.isEnabled();
            if (demoEnabled) {
                weaknesses = demoModeService.buildWeaknesses(session.getUserId(), session.getId());
            } else {
                weaknesses = extractWeaknesses(session, report);
            }
            userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
                .eq(UserWeakness::getSessionId, session.getId()));
            for (UserWeakness weakness : weaknesses) {
                userWeaknessMapper.insert(weakness);
            }
        } catch (Exception exception) {
            log.warn("Failed to persist weaknesses for session {}", session.getId(), exception);
        }
    }

    private List<UserWeakness> extractWeaknesses(InterviewSession session, String report) throws Exception {
        String content = llmRouter.chatWithSnapshot(
            session.getLlmProvider(),
            session.getLlmModel(),
            List.of(
                Map.of("role", "system", "content", """
                    你是面试分析助手。请只输出严格 JSON 数组，不要输出 Markdown。
                    每个元素必须包含 category 和 description 两个字段。
                    示例：[{"category":"JVM 内存模型","description":"对堆、栈和 GC 场景回答不完整"}]
                    """),
                Map.of("role", "user", "content", "请从以下面试报告中提取 1 到 5 个候选人的薄弱点：\n" + report)
            )
        );
        String json = stripJsonFence(content);
        List<WeaknessExtractionItem> items = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        java.util.ArrayList<UserWeakness> weaknesses = new java.util.ArrayList<>();
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

    public record ReportJob(Long sessionId, Long userId) {}
    private record WeaknessExtractionItem(String category, String description) {}
}
