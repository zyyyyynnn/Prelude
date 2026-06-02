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
            "为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。",
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(12));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 0), 4, createdAt.plusMinutes(13));
    }

    private List<QnaPair> javaScript() {
        return List.of(
            new QnaPair("warmup", TECHNICAL_STAGE_PROMPT, 
                "你好，我看你简历上写了负责高并发电商秒杀系统。我们单刀直入，在流量洪峰打过来的时候，你的 Redis 预扣减是怎么保证原子性的？", 
                "为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。"),
            new QnaPair("warmup", null, 
                "使用 Lua 脚本确实能保证单机原子性。但如果秒杀库存分片在了多个 Redis 节点上，单个 Lua 脚本还能搞定吗？", 
                "如果是集群架构，我们会通过 Hash Tag 将同一个商品的所有库存分片路由到同一个 Redis 节点，这样 Lua 脚本依然可以保证局部原子性。如果要做全局库存聚合，则会利用 Redisson 的分布式锁做更粗粒度的控制。"),
            new QnaPair("technical", DEEP_DIVE_STAGE_PROMPT, 
                "既然提到了通过 RocketMQ 异步削峰落库。如果 Redis 扣减成功，但丢给 RocketMQ 的 Half Message 发送超时了，你怎么收场？", 
                "这时候其实本地事务还没有提交。如果 Half Message 超时，RocketMQ 会主动回调我们的 TransactionCheckListener。我们在回调中检查 Redis 的流水状态或者本地的消息防重表，来决定是 Rollback 还是 Commit。同时前端会有轮询机制做最终确认。"),
            new QnaPair("technical", null, 
                "本地消息表方案确实兜底了最终一致性。但考虑到大促期间本地数据库 TPS 本来就面临极限，如果每笔失败的消息都强行落库，数据库岂不是会当场宕机？", 
                "这是一个非常好的点。我们在生产中并不会每次都去查表。首先我们会做 MQ 的自动重试机制；其次，对于确实需要补偿的，我们把消息表放在独立的补偿库，而不是核心交易库；或者改用日志解析（如 Canal）去对比数据差异，将对核心库的侵入降到最低。"),
            new QnaPair("deep_dive", null, 
                "那我们再往深挖一步。在 Redisson 实现的分布式锁防超卖环节，如果你拿到锁的微服务节点突然发生了长时间的 Full GC（STW），导致 WatchDog 看门狗未能续期锁被释放，被其他节点抢走，等 STW 结束这个微服务继续执行，引发了超卖。这种情况你怎么防御？", 
                "如果发生这种极端 STW 导致的锁失效，单纯依赖锁是不够的。我们会在数据库层加一个 `update stock = stock - 1 where id = ? and stock > 0` 的乐观锁兜底。即使 STW 后继续执行，SQL 执行时会发现库存不足而回滚，保证绝对不会超卖。"),
            new QnaPair("deep_dive", null, 
                "采用乐观锁或者在数据库层做行级悲观锁兜底是一个思路。但在分库分表（ShardingSphere）的场景下，跨节点的行锁往往会退化为分布式事务，极大拉低吞吐量，这又怎么取舍？", 
                "在追求极致吞吐的大促场景下，我们会尽量避免分布式事务。我们的做法是将库存拆分成更细的维度（比如按照用户 ID 路由到特定库），或者干脆不保证严格一致，而是采用基于流水表的异步核对系统（T+1 或者延迟对账），通过后续发放补偿券来对冲极小概率的超卖损失，因为这种工程取舍在商业上是完全可接受的。"),
            new QnaPair("closing", CLOSING_STAGE_PROMPT, 
                "好的，最后一个问题。抛开技术方案，如果这次双十一秒杀让你重新从头设计，在预算不变的情况下，你会优先在哪个环节做架构降级或减负？", 
                "我会优先在查询链路上做彻底的降级。通过在 CDN 或者边缘节点（甚至客户端本地缓存）做静态化，把绝大部分查询流量在到达网关前拦截。对于后端的核心服务，只保留扣库存这一个极简接口，剥离掉所有非核心的营销规则计算，把服务器算力真正用到刀刃上。")
        );
    }

    private List<QnaPair> frontendScript() {
        return List.of(
            new QnaPair("warmup", TECHNICAL_STAGE_PROMPT, 
                "你好，我看你在简历中主导了微前端的落地和海量数据的虚拟列表重构。先说微前端，基于 qiankun 做子应用隔离时，你如何解决由于不同团队的技术栈和版本不同导致的全局 CSS 污染和 JS 变量冲突？", 
                "对于 JS 变量，qiankun 内部利用 Proxy 实现了浏览器的 JS 沙箱隔离，这能挡掉大部分全局污染。但对于 CSS，原生的 strictStyleIsolation 会带来挂载在 body 上的弹窗组件样式丢失问题。我们最终采用了 experimentalStyleIsolation 为 CSS 规则动态增加前缀，结合 BEM 命名规范和 CSS Modules 彻底解决了样式冲突。"),
            new QnaPair("warmup", null, 
                "面对多实例子应用的 Pinia 状态隔离机制，你有没有遇到过子应用切换时状态泄漏的问题？你们是如何防污染的？", 
                "遇到过。Pinia 默认是单例模式挂载在 Vue App 实例上的，子应用卸载时如果不主动清理，状态仍会驻留在内存。我们在子应用的 unmount 生命周期中，主动调用 $reset() 重置所有核心 store，并配合 qiankun 的生命周期钩子，确保子应用的状态生命周期与微前端框架严格绑定。"),
            new QnaPair("technical", DEEP_DIVE_STAGE_PROMPT, 
                "来聊聊 10 万级数据的虚拟列表。当用户快速滚动（比如鼠标拖拽滚动条）时，经常会出现白屏或者掉帧闪烁。你认为这是纯粹的 DOM 渲染瓶颈，还是计算层面的问题？怎么解决？", 
                "两者都有。快速滚动时，一方面是大量的可见数据需要重新计算起始索引（startIndex）和偏移量，另一方面是反复的 DOM 替换和重绘。为了解决白屏，我们并不是只渲染可视区域，而是上下各自额外缓冲了一屏数据。同时，把滚动事件的监听通过 requestAnimationFrame 做节流，把高度计算放到 Web Worker 中进行，避免阻塞主线程。"),
            new QnaPair("technical", null, 
                "对于高度不固定的长列表，你们在 ResizeObserver 回调中更新高度如果引发重排（Reflow），会导致严重的阻塞。你们是怎么避免循环触发布局更新的？", 
                "高度不确定的虚拟列表确实是最难搞的。我们采用的是预估高度 + 异步修正的策略。初始给一个默认预估高度渲染出滚动条，然后在 DOM 渲染后利用 ResizeObserver 获取真实高度更新缓存数组。为了避免重排引发的卡顿，我们将高度修正的操作批量推迟到了 nextTick 或者下一次 rAF 帧中合并执行，绝不在当前渲染帧里强制读取 offsetHeight 引发同步重排。"),
            new QnaPair("deep_dive", null, 
                "在实时在线视频面试系统中，你在简历上写到用 Web Audio API 重排音频切片解决网络抖动。如果客户端的网络发生严重的丢包（10%以上丢包率），单纯的重排根本无用，你会如何在前端层面做平滑处理？", 
                "如果在传输层我们走的是基于 UDP 的 WebRTC（RTP/RTCP协议），对于弱网我们会在信令握手时开启前向纠错（FEC）或者重传机制（NACK）。如果是基于 WebSocket 收到的分包，在前端 AudioWorklet 接收时如果发现 seq 不连续，我们会在缓冲区加入短时的静音包或者利用算法做基于时域的音频拉伸（Time Stretching）来平滑过渡，实在严重时则直接降级为文字模式。"),
            new QnaPair("closing", CLOSING_STAGE_PROMPT, 
                "如果现在要将整个微前端框架和在线面试系统打包迁移到鸿蒙原生或者桌面端 Electron，你觉得前端现有的架构设计中，阻力最大的是哪一部分？", 
                "阻力最大的肯定是平台底层 API 的差异。例如 WebRTC 那些多媒体 API 和微前端依赖的 iframe / Proxy 沙箱，在鸿蒙的 ArkUI 或者某些严格受限的 Webview 里根本不兼容。我们需要把底层的网络通信和音视频采集抽象出一层适配器接口（Adapter），不同平台注入不同的实现，前端业务代码只依赖这层 Adapter，这样才能低成本迁移。")
        );
    }

    private List<QnaPair> algorithmScript() {
        return List.of(
            new QnaPair("warmup", TECHNICAL_STAGE_PROMPT, 
                "你好，我看你负责过百亿级推荐系统的迭代。传统双塔结构在召回阶段最大的痛点是它的交互仅限于最终 Embedding 的内积，缺乏细粒度的特征交叉。这个问题你们是怎么缓解的？", 
                "是的，双塔模型为了保证 Faiss 的海量毫秒级检索，强行将 User 和 Item 在顶层才做点积，导致底层的细粒度特征完全没有交互。我们的缓解思路是在粗排阶段补强特征交叉。我们引入了类似 SENet 或 DCN（Deep & Cross Network）的轻量级网络对召回结果做快速打分，在不显著增加线上耗时的前提下把强交叉信息补了回来。"),
            new QnaPair("warmup", null, 
                "在多目标优化的排序模型中（比如同时预估点击率和转化率的 ESSM 模型），你是如何处理样本空间偏置（SSB）和数据稀疏（DS）问题的？", 
                "针对样本空间偏置（SSB），我们使用了全样本空间进行训练，利用 CTR 的预估值作为 CVR 预估的权重或者直接构建 CVR 的条件概率模型（如 ESSM），使得 CVR 模型能看到所有曝光样本而不是仅仅点击样本。对于数据稀疏（DS），我们通过多任务学习（MTL）共享底层 Embedding 表，让稀疏目标的特征从高频目标的丰富样本中获得更新信号，从而缓解冷启动问题。"),
            new QnaPair("technical", DEEP_DIVE_STAGE_PROMPT, 
                "我们转到大语言模型微调。你提到使用 LoRA 微调 Llama-3。在多机多卡的分布式训练中，如果卡间通信成为了瓶颈，特别是在反向传播更新梯度时，你对 Ring AllReduce 机制和显存优化策略有实战经验吗？", 
                "LoRA 本质是低秩自适应，参数量较小，通常不需要极致的并行。但如果我们做全参微调或是千亿级模型，确实需要面对通信瓶颈。对于显存优化，我们会使用 ZeRO-2 或 ZeRO-3 将优化器状态和梯度分片到不同的 GPU 上；对于通信瓶颈，我们会采用梯度累积减少 AllReduce 频次，并在物理拓扑上利用 NVLink 保证机内通信带宽，跨机时利用 InfiniBand 结合 Ring AllReduce 甚至 Tree AllReduce 来降低网络延迟。"),
            new QnaPair("technical", null, 
                "在减少垂直领域专业知识问答幻觉时，你提到优化了 RLHF 对齐策略。RLHF 中如果奖励模型（Reward Model）的评分出现过度优化（Reward Hacking），即模型学会了迎合打分机制却不解决实际问题，你怎么应对？", 
                "为了解决 Reward Hacking，我们引入了 KL 散度（Kullback-Leibler divergence）惩罚项。在 PPO 阶段，我们会限制当前 Policy 模型生成的分布不要偏离初始 SFT（Supervised Fine-Tuning）模型太远。同时我们在训练期间会定期让人类标注员（Human-in-the-loop）介入，抽样检查高分回复是否真实，并动态调整或回退奖励模型的训练集。"),
            new QnaPair("deep_dive", null, 
                "线上问题排查方面，如果有天早上你们发现线上的点击率指标断崖式下跌，但服务本身的 QPS 和错误率完全正常。你作为推荐算法工程师，第一时间会怎么排查？", 
                "如果服务层没问题，我会立刻排查数据分布和特征漂移。首先我会去对比最近几小时的实时日志和昨天同期的特征覆盖率，看是不是某些关键字段（比如用户年龄、设备类型）的数据源断流导致被大量填了默认值。其次，我会看下游大推池里的物品分布有没有发生突变（比如某个热点新闻引发的马太效应）。最后，检查前一天晚上是否有过模型重训或特征工程上线的动作导致的隐式 Bug。"),
            new QnaPair("closing", CLOSING_STAGE_PROMPT, 
                "最后，你在评估离线模型时，有没有遇到过离线 AUC 上涨，但线上 A/B 实验反而收益为负的情况？过分迷信 AUC 等离线指标会有什么问题？", 
                "非常常见。离线 AUC 的提升很多时候仅仅是因为模型学会了利用了某个强漏斗特征，或者受到了 Position Bias（位置偏见）的影响，导致离线数据评估看起来很美。线上我们不仅看 CTR，还要看用户留存和商业变现（如 RPM），这些目标很多时候是互相制约的。我们在 A/B 实验时，必须严格遵守流量正交和显著性检验（p-value < 0.05），避免将短期波动当作长期收益发布上线。")
        );
    }

    private void createFinishedSession(
        Long userId, Resume resume, PositionTemplate position, LocalDateTime createdAt,
        ScoreSeed score, List<WeaknessSeed> weaknesses, List<QnaPair> script
    ) {
        InterviewSession session = buildSession(userId, resume, position, STATUS_FINISHED, createdAt, resolveReport(position.getName()));

        insertStage(session.getId(), "warmup", createdAt, createdAt.plusMinutes(8));
        insertStage(session.getId(), "technical", createdAt.plusMinutes(8), createdAt.plusMinutes(18));
        insertStage(session.getId(), "deep_dive", createdAt.plusMinutes(18), createdAt.plusMinutes(28));
        insertStage(session.getId(), "closing", createdAt.plusMinutes(28), createdAt.plusMinutes(34));

        int seq = 0;
        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), seq++, createdAt);
        
        for (int i = 0; i < script.size(); i++) {
            QnaPair pair = script.get(i);
            if (pair.systemPrompt() != null) {
                insertMessage(session.getId(), ROLE_SYSTEM, pair.systemPrompt(), seq++, createdAt.plusMinutes(1 + i * 2));
            }
            insertMessage(session.getId(), ROLE_ASSISTANT, pair.aiQuestion(), seq++, createdAt.plusMinutes(2 + i * 2));
            insertMessage(session.getId(), ROLE_USER, pair.userAnswer(), seq++, createdAt.plusMinutes(3 + i * 2));
        }

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

    private record QnaPair(String stageName, String systemPrompt, String aiQuestion, String userAnswer) {
    }
}
