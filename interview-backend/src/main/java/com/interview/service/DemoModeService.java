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
    private static final String DEMO_JAVA_POSITION_NAME = "Java 后端工程师";
    private static final String DEMO_FRONTEND_POSITION_NAME = "前端工程师";
    private static final String DEMO_ALGORITHM_POSITION_NAME = "算法工程师";
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
        if (DEMO_FRONTEND_POSITION_NAME.equals(targetPosition)) {
            return frontendReport();
        }
        if (DEMO_ALGORITHM_POSITION_NAME.equals(targetPosition)) {
            return algorithmReport();
        }
        String template = readText("demo/report-template.md");
        return template.replace("{{position}}", targetPosition == null ? "目标岗位" : targetPosition);
    }

    private String frontendReport() {
        return """
            # 面试评估报告

            ## 面试概览
            - 目标岗位：前端工程师
            - 结论：候选人具备较完整的前端工程化和页面性能意识，适合继续深入评估。

            ## 三维评分
            - 技术能力：8/10
            - 表达清晰度：7/10
            - 逻辑思维：7/10

            ## 优势总结
            - 能够围绕组件拆分、状态管理和页面链路说明实现思路
            - 对性能排查、接口耗时和渲染边界有基本判断能力
            - 能将交互细节与真实使用体验关联起来

            ## 改进建议
            1. 补强浏览器性能指标、资源加载和渲染链路的量化说明
            2. 在复杂组件状态归属和复用边界上给出更清晰的取舍
            3. 对移动端适配、键盘焦点和无障碍状态说明可以更完整

            ## 总结
            整体表现稳定，具备继续进入前端专项面试的基础。
            """;
    }

    private String algorithmReport() {
        return """
            # 面试评估报告

            ## 面试概览
            - 目标岗位：算法工程师
            - 结论：候选人能按数据、模型和评估链路组织回答，但实验复现和误差分析仍需加强。

            ## 三维评分
            - 技术能力：7/10
            - 表达清晰度：6/10
            - 逻辑思维：8/10

            ## 优势总结
            - 能够从样本、特征、基线方案和指标口径拆解问题
            - 对离线评估和线上表现差异有基本排查路径
            - 回答结构较清楚，能说明数据分布变化带来的影响

            ## 改进建议
            1. 补强时间复杂度、空间复杂度和边界规模的量化表达
            2. 在验证集划分、误差分析和指标选择上给出更具体示例
            3. 对实验版本、参数记录和失败样本复盘说明可以更严谨

            ## 总结
            整体具备算法岗继续评估的基础，但需要提高实验细节和表达稳定性。
            """;
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
        PositionTemplate javaPosition = requireDemoPosition(DEMO_JAVA_POSITION_NAME);
        PositionTemplate frontendPosition = requireDemoPosition(DEMO_FRONTEND_POSITION_NAME);
        PositionTemplate algorithmPosition = requireDemoPosition(DEMO_ALGORITHM_POSITION_NAME);
        Resume resume = insertResume(user.getId(), "demo-resume.pdf", LocalDateTime.of(2026, 4, 22, 16, 40));

        createOngoingSession(user.getId(), resume, javaPosition);
        createFinishedSession(
            user.getId(),
            resume,
            javaPosition,
            LocalDateTime.of(2026, 4, 22, 10, 0),
            new ScoreSeed(7, 8, 7),
            List.of(
                new WeaknessSeed("JVM 与并发", "对线程调度、锁竞争和 JVM 运行细节的回答还不够深入。"),
                new WeaknessSeed("数据库优化", "能够提出方向，但缺少执行计划、索引命中和慢查询定位的具体说明。")
            ),
            javaScript()
        );
        createFinishedSession(
            user.getId(),
            resume,
            frontendPosition,
            LocalDateTime.of(2026, 4, 20, 16, 10),
            new ScoreSeed(8, 7, 7),
            List.of(
                new WeaknessSeed("浏览器性能", "能说明首屏优化方向，但对指标拆解和定位链路还可以更具体。"),
                new WeaknessSeed("组件边界", "知道拆分组件，但对状态归属和复用边界的说明还不够凝练。")
            ),
            frontendScript()
        );
        createFinishedSession(
            user.getId(),
            resume,
            algorithmPosition,
            LocalDateTime.of(2026, 4, 18, 15, 30),
            new ScoreSeed(7, 6, 8),
            List.of(
                new WeaknessSeed("复杂度分析", "能够给出解题方向，但对边界规模和时间复杂度的量化不够稳定。"),
                new WeaknessSeed("模型评估", "知道使用验证集和指标，但对误差分析、特征贡献的表达还可以加强。")
            ),
            algorithmScript()
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
            "我主要做后端这一块，从登录鉴权、简历上传，到面试会话、SSE 流式回复和报告落库都参与了。实际开发里我花时间最多的是把阶段推进和消息记录串成闭环，保证后面回放和看板都有数据可用。",
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(12));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 0), 4, createdAt.plusMinutes(13));
    }

    private SessionScript javaScript() {
        return new SessionScript(
            "一开始我也想过直接写在一个服务里，但很快发现会越来越乱。后来拆成简历解析、会话记录和报告生成三块，是为了让每块职责清楚一点。比如 Demo 模式可以复用会话和报告流程，只把模型调用替换成脚本数据。",
            "我会先确认慢在哪里，而不是直接加缓存。比如先看接口耗时、SQL 执行时间和返回数据量。如果是列表查询扫表，我会先补索引和分页；如果读多写少、结果变化不频繁，再考虑加一层缓存。",
            "我会把它当成两个问题处理：一是连接断了以后，emitter 的 completion、timeout、error 回调里要释放资源；二是消息不能靠内存状态判断是否写过，最终还是要用数据库里的会话和消息记录来兜底，避免重复写或漏写。",
            "如果继续做，我会先补评分解释。现在报告能给分，但用户更关心为什么扣分、下一次该怎么改。把每个分数和具体回答片段关联起来，会比单纯多做几个页面更有价值。"
        );
    }

    private SessionScript frontendScript() {
        return new SessionScript(
            "我主要负责前端工作台和回放页面，把简历选择、阶段问答、报告预览放在同一条使用路径里。后面又把 Demo 截图模式单独整理出来，保证展示页面稳定。",
            "我会先看首屏资源、接口耗时和组件渲染次数，再判断是请求慢、包体大还是页面重复渲染。如果是列表区域，我会优先控制数据量和渲染边界，而不是直接上复杂缓存。",
            "我会把状态分成服务端真实状态、页面临时输入和展示派生状态。比如当前阶段和消息记录以接口返回为准，输入框和加载态只留在组件本地，避免刷新或切路由后状态混乱。",
            "如果继续做，我会先补交互细节和无障碍状态，比如按钮禁用原因、键盘焦点和移动端布局。这样比单纯多做页面更能提升真实使用质量。"
        );
    }

    private SessionScript algorithmScript() {
        return new SessionScript(
            "我会先把问题拆成数据输入、特征处理、基线方案和评估指标四部分。毕业设计里虽然不是完整算法平台，但评分和薄弱点分析也需要稳定的输入输出边界。",
            "我不会只看一次跑分，而会先确定样本规模、标签质量和指标口径。比如分类问题要看准确率以外的召回、混淆矩阵，排序问题则要看 TopK 命中和误差集中在哪些样本。",
            "如果线上表现和离线评估差很多，我会先排查数据分布是否变化，再看特征是否泄露、样本是否偏斜。必要时会拆分人群或场景，避免一个平均指标掩盖主要问题。",
            "下一步我会先补可复现实验记录，包括数据版本、参数、指标和失败样本。这样后续调参或替换模型时，才能判断到底是模型改进还是数据偶然波动。"
        );
    }

    private void createFinishedSession(
        Long userId,
        Resume resume,
        PositionTemplate position,
        LocalDateTime createdAt,
        ScoreSeed score,
        List<WeaknessSeed> weaknesses,
        SessionScript script
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
            script.warmupAnswer(),
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(8));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 1), 4, createdAt.plusMinutes(9));
        insertMessage(
            session.getId(),
            ROLE_USER,
            script.technicalAnswer(),
            5,
            createdAt.plusMinutes(13)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, DEEP_DIVE_STAGE_PROMPT, 6, createdAt.plusMinutes(18));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("deep_dive", 0), 7, createdAt.plusMinutes(19));
        insertMessage(
            session.getId(),
            ROLE_USER,
            script.deepDiveAnswer(),
            8,
            createdAt.plusMinutes(23)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, CLOSING_STAGE_PROMPT, 9, createdAt.plusMinutes(28));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("closing", 0), 10, createdAt.plusMinutes(29));
        insertMessage(
            session.getId(),
            ROLE_USER,
            script.closingAnswer(),
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

    private PositionTemplate requireDemoPosition(String positionName) {
        PositionTemplate position = positionTemplateMapper.selectOne(new LambdaQueryWrapper<PositionTemplate>()
            .eq(PositionTemplate::getName, positionName)
            .last("LIMIT 1"));

        if (position == null) {
            throw BusinessException.badRequest("演示岗位模板不存在: " + positionName);
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

    private record SessionScript(
        String warmupAnswer,
        String technicalAnswer,
        String deepDiveAnswer,
        String closingAnswer
    ) {
    }
}
