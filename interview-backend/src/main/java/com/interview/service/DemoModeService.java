package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.config.DemoProperties;
import com.interview.dto.ResumeProjectDto;
import com.interview.dto.ResumeUploadResponse;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.PositionTemplate;
import com.interview.entity.Resume;
import com.interview.entity.ScoreHistory;
import com.interview.entity.User;
import com.interview.entity.UserWeakness;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DemoModeService {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_EMAIL = "demo@example.com";
    private static final String DEMO_PASSWORD_HASH = "$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2";
    private static final String DEMO_API_KEY_PLACEHOLDER = "demo-key-placeholder";
    private static final String DEMO_POSITION_NAME = "Java 后端工程师";
    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String TECHNICAL_STAGE_PROMPT = "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。";
    private static final String DEEP_DIVE_STAGE_PROMPT = "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。";
    private static final String CLOSING_STAGE_PROMPT = "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。";

    private final DemoProperties demoProperties;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;

    public boolean isEnabled() {
        return demoProperties.isEnabled();
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset() {
        assertEnabled();

        User user = ensureDemoUser();
        List<Long> sessionIds = interviewSessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, user.getId()))
            .stream()
            .map(InterviewSession::getId)
            .toList();

        if (!sessionIds.isEmpty()) {
            interviewMessageMapper.delete(new LambdaQueryWrapper<InterviewMessage>()
                .in(InterviewMessage::getSessionId, sessionIds));
            interviewStageMapper.delete(new LambdaQueryWrapper<InterviewStage>()
                .in(InterviewStage::getSessionId, sessionIds));
            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .in(ScoreHistory::getSessionId, sessionIds));
            userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
                .in(UserWeakness::getSessionId, sessionIds));
            interviewSessionMapper.delete(new LambdaQueryWrapper<InterviewSession>()
                .in(InterviewSession::getId, sessionIds));
        }

        resumeMapper.delete(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getUserId, user.getId()));

        DemoLlmConfigFixture fixture = readJson("demo/llm-config.json", new TypeReference<>() {
        });
        user.setEmail(DEMO_EMAIL);
        user.setLlmProvider(fixture.providerKey());
        user.setLlmModel(fixture.model());
        user.setLlmApiKeyEncrypted(DEMO_API_KEY_PLACEHOLDER);
        userMapper.updateById(user);

        seedStoryline(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse createDemoResume(Long userId, String fileName) {
        assertEnabled();
        Resume resume = insertResume(userId, fileName, LocalDateTime.now());
        DemoResumeFixture fixture = readJson("demo/resume-template.json", new TypeReference<>() {
        });
        return new ResumeUploadResponse(resume.getId(), fixture.skills(), fixture.projects());
    }

    public String resolveScriptedReply(String stageName, int replyIndex) {
        assertEnabled();
        DemoStageRepliesFixture fixture = readJson("demo/stage-replies.json", new TypeReference<>() {
        });
        List<String> replies = fixture.replies().get(stageName);
        if (replies == null || replies.isEmpty()) {
            throw BusinessException.badRequest("演示阶段回复未配置");
        }
        int index = Math.max(0, Math.min(replyIndex, replies.size() - 1));
        return replies.get(index);
    }

    public void streamReply(String reply, Consumer<String> consumer) {
        assertEnabled();
        if (reply == null || reply.isBlank()) {
            return;
        }
        int chunkSize = Math.max(1, demoProperties.getChunkSize());
        int delayMs = Math.max(0, demoProperties.getStreamDelayMs());
        for (int start = 0; start < reply.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, reply.length());
            consumer.accept(reply.substring(start, end));
            if (delayMs > 0 && end < reply.length()) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public String resolveReport(String targetPosition) {
        assertEnabled();
        String template = readText("demo/report-template.md");
        return template.replace("{{position}}", targetPosition == null ? "目标岗位" : targetPosition);
    }

    public List<UserWeakness> buildWeaknesses(Long userId, Long sessionId) {
        assertEnabled();
        List<DemoWeaknessFixture> items = readJson("demo/weaknesses.json", new TypeReference<>() {
        });
        return items.stream()
            .map(item -> buildWeakness(userId, sessionId, item.category(), item.description(), LocalDateTime.now()))
            .toList();
    }

    public String maskApiKey(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        return "****demo";
    }

    public String nextStoredApiKey(String requestApiKey, String currentStoredApiKey) {
        if (requestApiKey == null) {
            return currentStoredApiKey;
        }
        return requestApiKey.isBlank() ? null : DEMO_API_KEY_PLACEHOLDER;
    }

    private void seedStoryline(User user) {
        PositionTemplate position = requireDemoPosition();
        Resume resume = insertResume(user.getId(), "demo-resume.pdf", LocalDateTime.of(2026, 4, 22, 16, 40));

        createOngoingSession(user.getId(), resume, position);
        createFinishedSession(
            user.getId(),
            resume,
            position,
            LocalDateTime.of(2026, 4, 22, 10, 0),
            new ScoreSeed(7, 8, 7),
            List.of(
                new WeaknessSeed("JVM 与并发", "对线程调度、锁竞争和 JVM 运行细节的回答还不够深入。"),
                new WeaknessSeed("数据库优化", "能够提出方向，但缺少执行计划、索引命中和慢查询定位的具体说明。")
            )
        );
        createFinishedSession(
            user.getId(),
            resume,
            position,
            LocalDateTime.of(2026, 4, 18, 15, 30),
            new ScoreSeed(6, 7, 6),
            List.of(
                new WeaknessSeed("异常与幂等", "知道要做异常处理和重复提交防护，但边界场景覆盖还不够完整。"),
                new WeaknessSeed("系统设计表达", "可以说明模块职责，但对接口边界、扩展性与取舍讲解还不够凝练。")
            )
        );
    }

    private void createOngoingSession(Long userId, Resume resume, PositionTemplate position) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 23, 14, 0);
        InterviewSession session = buildSession(userId, resume, position, STATUS_ONGOING, createdAt, null);

        insertStage(session.getId(), "warmup", createdAt, createdAt.plusMinutes(12));
        insertStage(session.getId(), "technical", createdAt.plusMinutes(12), null);

        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), 0, createdAt);
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("warmup", 0), 1, createdAt.plusMinutes(1));
        insertMessage(
            session.getId(),
            ROLE_USER,
            "我主要负责后端接口、JWT 登录、SSE 流式问答和面试报告落库，重点做了会话记录与阶段推进的闭环。",
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(12));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 0), 4, createdAt.plusMinutes(13));
    }

    private void createFinishedSession(
        Long userId,
        Resume resume,
        PositionTemplate position,
        LocalDateTime createdAt,
        ScoreSeed score,
        List<WeaknessSeed> weaknesses
    ) {
        InterviewSession session = buildSession(userId, resume, position, STATUS_FINISHED, createdAt, resolveReport(position.getName()));

        insertStage(session.getId(), "warmup", createdAt, createdAt.plusMinutes(8));
        insertStage(session.getId(), "technical", createdAt.plusMinutes(8), createdAt.plusMinutes(18));
        insertStage(session.getId(), "deep_dive", createdAt.plusMinutes(18), createdAt.plusMinutes(28));
        insertStage(session.getId(), "closing", createdAt.plusMinutes(28), createdAt.plusMinutes(34));

        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), 0, createdAt);
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("warmup", 1), 1, createdAt.plusMinutes(1));
        insertMessage(
            session.getId(),
            ROLE_USER,
            "我把简历解析、会话记录和报告生成拆成独立服务，核心考虑是降低耦合，便于在 Demo 和真实模式之间复用流程。",
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(8));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 1), 4, createdAt.plusMinutes(9));
        insertMessage(
            session.getId(),
            ROLE_USER,
            "高并发下我会先看慢查询和命中率，再判断是否需要缓存；如果列表查询稳定且读多写少，会优先补索引和结果缓存。",
            5,
            createdAt.plusMinutes(13)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, DEEP_DIVE_STAGE_PROMPT, 6, createdAt.plusMinutes(18));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("deep_dive", 0), 7, createdAt.plusMinutes(19));
        insertMessage(
            session.getId(),
            ROLE_USER,
            "SSE 断连时我会在 emitter 的 timeout 和 completion 回调里释放资源，并通过会话状态表保证阶段与消息不会重复写入。",
            8,
            createdAt.plusMinutes(23)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, CLOSING_STAGE_PROMPT, 9, createdAt.plusMinutes(28));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("closing", 0), 10, createdAt.plusMinutes(29));
        insertMessage(
            session.getId(),
            ROLE_USER,
            "如果继续完善，我会先补评分解释和 Demo 数据播种，让面试结果、回放和看板能形成一条完整答辩链路。",
            11,
            createdAt.plusMinutes(31)
        );

        ScoreHistory history = new ScoreHistory();
        history.setUserId(userId);
        history.setSessionId(session.getId());
        history.setTechnicalScore(score.technical());
        history.setExpressionScore(score.expression());
        history.setLogicScore(score.logic());
        history.setCreatedAt(createdAt.plusMinutes(35));
        scoreHistoryMapper.insert(history);

        for (int index = 0; index < weaknesses.size(); index++) {
            WeaknessSeed weakness = weaknesses.get(index);
            userWeaknessMapper.insert(
                buildWeakness(userId, session.getId(), weakness.category(), weakness.description(), createdAt.plusMinutes(36 + index))
            );
        }
    }

    private InterviewSession buildSession(
        Long userId,
        Resume resume,
        PositionTemplate position,
        String status,
        LocalDateTime createdAt,
        String report
    ) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume.getId());
        session.setPositionId(position.getId());
        session.setTargetPosition(position.getName());
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-chat");
        session.setStatus(status);
        session.setSummaryReport(report);
        session.setCreatedAt(createdAt);
        interviewSessionMapper.insert(session);
        return session;
    }

    private Resume insertResume(Long userId, String fileName, LocalDateTime createdAt) {
        DemoResumeFixture fixture = readJson("demo/resume-template.json", new TypeReference<>() {
        });

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(fileName);
        resume.setRawText(fixture.rawText());
        resume.setParsedSkills(writeJson(fixture.skills()));
        resume.setParsedProjects(writeJson(fixture.projects()));
        resume.setCreatedAt(createdAt);
        resumeMapper.insert(resume);
        return resume;
    }

    private void insertStage(Long sessionId, String stageName, LocalDateTime startedAt, LocalDateTime endedAt) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(stageName);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        interviewStageMapper.insert(stage);
    }

    private void insertMessage(Long sessionId, String role, String content, int seqNum, LocalDateTime createdAt) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        message.setCreatedAt(createdAt);
        interviewMessageMapper.insert(message);
    }

    private UserWeakness buildWeakness(Long userId, Long sessionId, String category, String description, LocalDateTime createdAt) {
        UserWeakness weakness = new UserWeakness();
        weakness.setUserId(userId);
        weakness.setSessionId(sessionId);
        weakness.setCategory(category);
        weakness.setDescription(description);
        weakness.setCreatedAt(createdAt);
        return weakness;
    }

    private PositionTemplate requireDemoPosition() {
        PositionTemplate position = positionTemplateMapper.selectOne(new LambdaQueryWrapper<PositionTemplate>()
            .eq(PositionTemplate::getName, DEMO_POSITION_NAME)
            .last("LIMIT 1"));

        if (position == null) {
            throw BusinessException.badRequest("演示岗位模板不存在");
        }

        return position;
    }

    private User ensureDemoUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, DEMO_USERNAME)
            .last("LIMIT 1"));
        if (user != null) {
            return user;
        }

        User demoUser = new User();
        demoUser.setUsername(DEMO_USERNAME);
        demoUser.setPassword(DEMO_PASSWORD_HASH);
        demoUser.setEmail(DEMO_EMAIL);
        demoUser.setCreatedAt(LocalDateTime.now());
        userMapper.insert(demoUser);
        return demoUser;
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw BusinessException.badRequest("演示模式未启用");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw BusinessException.badRequest("演示夹具序列化失败");
        }
    }

    private String readText(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取演示夹具失败: " + path);
        }
    }

    private <T> T readJson(String path, TypeReference<T> typeReference) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取演示夹具失败: " + path);
        }
    }

    private record DemoLlmConfigFixture(String providerKey, String model, String apiKeyMasked) {
    }

    private record DemoResumeFixture(
        List<String> skills,
        List<ResumeProjectDto> projects,
        String rawText
    ) {
    }

    private record DemoStageRepliesFixture(Map<String, List<String>> replies) {
    }

    private record DemoWeaknessFixture(String category, String description) {
    }

    private record ScoreSeed(int technical, int expression, int logic) {
    }

    private record WeaknessSeed(String category, String description) {
    }
}
