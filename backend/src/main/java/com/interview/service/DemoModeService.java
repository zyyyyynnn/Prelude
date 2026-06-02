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

    public String resolveMockJudge(String stageName, int replyIndex) {
        assertEnabled();
        int score = 7 + (replyIndex % 3);
        String hint;
        if ("warmup".equals(stageName)) {
            hint = "开场表现自然，对过往项目描述清晰。";
        } else if ("technical".equals(stageName)) {
            hint = "技术概念阐述清晰，若能结合具体代码实现则更佳。";
        } else if ("deep_dive".equals(stageName)) {
            hint = "逻辑推导合理，但在底层机制的理解上还有提升空间。";
        } else {
            hint = "收尾陈述简明扼要，整体沟通顺畅。";
        }
        return String.format("{\"score\": %d, \"hint\": \"%s\"}", score, hint);
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
        String jsonPath = "demo/resume_java.json"; // 默认兜底
        if (fileName != null) {
            if (fileName.contains("Java")) jsonPath = "demo/resume_java.json";
            else if (fileName.contains("前端")) jsonPath = "demo/resume_frontend.json";
            else if (fileName.contains("算法")) jsonPath = "demo/resume_algorithm.json";
        }
        DemoResumeFixture fixture = readJson(jsonPath, new TypeReference<>() {});
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
        if (replyIndex >= replies.size()) {
            return "";
        }
        int index = Math.max(0, replyIndex);
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
        
        Resume javaResume = insertResume(user.getId(), "Java高级架构.pdf", LocalDateTime.of(2026, 4, 22, 16, 40));
        Resume frontendResume = insertResume(user.getId(), "大前端资深开发.pdf", LocalDateTime.of(2026, 4, 20, 16, 10));
        Resume algoResume = insertResume(user.getId(), "推荐算法工程师.pdf", LocalDateTime.of(2026, 4, 18, 15, 30));
        createOngoingSession(user.getId(), javaResume, javaPosition);
        createFinishedSession(
            user.getId(), javaResume, javaPosition, LocalDateTime.of(2026, 4, 22, 10, 0),
            new ScoreSeed(8, 9, 8),
            List.of(new WeaknessSeed("千亿级并发架构瓶颈", "对于跨数据中心的强一致性容灾方案及底层 Paxos 选主细节掌握不够纯熟。")),
            javaScript()
        );
        createFinishedSession(
            user.getId(), frontendResume, frontendPosition, LocalDateTime.of(2026, 4, 20, 16, 10),
            new ScoreSeed(7, 7, 7),
            List.of(
                new WeaknessSeed("WebRTC 底层信令协商", "能应用 WebRTC，但在穿透 NAT/Firewall (STUN/TURN) 时的 ICE 候选收集原理上解释含糊。"),
                new WeaknessSeed("复杂状态抽象", "面对多实例子应用的 Pinia 状态隔离机制没有给出完美的防污染方案。")
            ),
            frontendScript()
        );
        createFinishedSession(
            user.getId(), algoResume, algorithmPosition, LocalDateTime.of(2026, 4, 18, 15, 30),
            new ScoreSeed(5, 6, 6),
            List.of(
                new WeaknessSeed("分布式训练通信瓶颈", "未经历过真实的多机多卡环境，对 Ring AllReduce 机制和显存梯度累积原理完全陌生。"),
                new WeaknessSeed("线上问题排查", "特征漂移和线上指标断崖式下跌时的降级排查策略过于理论化，缺乏生产实操经验。"),
                new WeaknessSeed("评估指标局限", "过分迷信 AUC 等离线指标，对在线 A/B 实验的置信度检验和流量正交不了解。")
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
            "我对微服务架构和高并发场景比较有经验。在之前的秒杀系统中，我主导了核心链路的改造，采用 Redis 预扣减库存和 RocketMQ 异步落库的方式，抗住了 10 万并发。",
            "在 RocketMQ 异步扣减这里，我们会利用事务消息（Half Message）来保证上下游数据一致。如果下游的库存服务执行失败，我们会通过本地消息表加上定时任务的重试机制来保证最终一致性。遇到长时间失败，会自动转入死信队列并报警拦截。",
            "针对缓存击穿，我们并没有简单粗暴地设一个不过期的 Key。对于热点商品，我们利用 Redisson 分布式锁控制并发，只让第一个请求穿透去 DB 加载，其余请求都在外层等待并获取 Redis 返回。同时通过随机过期时间打散，避免大规模缓存雪崩。",
            "其实 ShardingSphere 的分库分表也带来了全表路由和分布式事务的隐患。在需要强一致的资金结算节点，我们最终还是用了 Seata 的 AT 模式，虽然牺牲了一定吞吐量，但保证了金融级准确性。"
        );
    }

    private SessionScript frontendScript() {
        return new SessionScript(
            "我在前端工程化和框架底层研究比较深，目前主力在用 Vue 3，之前带头用 qiankun 重构过微前端项目，也在处理海量数据渲染和实时音视频通讯方面有不少实战经验。",
            "关于虚拟列表（Virtual List），当数据达到 10 万级别时，如果滚动过快会出现短暂白屏。这是因为浏览器把大量计算放在主线程，导致渲染帧来不及被绘制（Frame Drop）。我们的解法是利用 requestAnimationFrame 将预渲染元素分割并在合适时机放入可见视口，并在外部加了防抖的 Scroll 监听。",
            "对于布局闪烁问题，我们确实发现 ResizeObserver 回调中的操作如果引发重排（Reflow），会导致阻塞。所以我们将高度的更新推迟到了 nextTick 里，并只对边界元素采用缓存机制计算高度。",
            "在视频面试项目中，Web Audio API 的 ScriptProcessorNode 已经被废弃，所以我们改用了 AudioWorklet。通过在独立的线程跑一个 worklet processor 去拼接从 WebSocket 收到的 PCM 分片，彻底避免了由于主线程卡顿而引起的破音或丢包杂音。"
        );
    }

    private SessionScript algorithmScript() {
        return new SessionScript(
            "我对搜索推荐和 CV / NLP 模型微调都有实际的项目经历。在推荐算法方面，我主推了 DeepFM 和 DIN 的迭代。在语言模型上，我负责过基于 LoRA 路线微调 Llama-3 解决垂直领域的领域知识幻觉。",
            "传统双塔结构在召回阶段最大的痛点是它的交互仅限于最终 Embedding 的内积，缺乏细粒度的特征交叉（User 与 Item 的底层特征交互不足）。这也是为什么后续我们在粗排阶段必须补上像 SENet 或类似网络做轻量特征交叉的原因。",
            "线上出现点击率突然下跌，我会第一时间去排查特征漂移和数据分布。有时可能是由于日志采集丢字段造成的默认值填充，也可能是当天活动带来的分布突变。但我一般通过看监控面板判断，不太接触底层的 flink 实时指标流排查。",
            "我觉得 AUC 这种离线排序能力其实不能完全等同于线上收益，因为存在 Position Bias 和曝光偏差。但真要到线上做 A/B 实验和分流，我们这边一般是交由工程组来做正交实验，我主要是给算法模型包。"
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
        session.setLlmModel("deepseek-v4-pro");
        session.setStatus(status);
        session.setSummaryReport(report);
        session.setCreatedAt(createdAt);
        interviewSessionMapper.insert(session);
        return session;
    }

    private Resume insertResume(Long userId, String fileName, LocalDateTime createdAt) {
        String jsonPath = "demo/resume_java.json"; // 默认兜底
        if (fileName != null) {
            if (fileName.contains("Java")) jsonPath = "demo/resume_java.json";
            else if (fileName.contains("前端")) jsonPath = "demo/resume_frontend.json";
            else if (fileName.contains("算法")) jsonPath = "demo/resume_algorithm.json";
        }
        DemoResumeFixture fixture = readJson(jsonPath, new TypeReference<>() {
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
