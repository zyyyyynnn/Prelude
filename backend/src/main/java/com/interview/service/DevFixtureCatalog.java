package com.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.dto.ResumeProjectDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DevFixtureCatalog {

    private static final String FRONTEND_POSITION_NAME = "前端工程师";
    private static final String ALGORITHM_POSITION_NAME = "算法工程师";
    private static final String TECHNICAL_STAGE_PROMPT = "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。";
    private static final String DEEP_DIVE_STAGE_PROMPT = "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。";
    private static final String CLOSING_STAGE_PROMPT = "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。";

    private final ObjectMapper objectMapper;

    public DevLlmConfigFixture llmConfig() {
        return readJson("demo/llm-config.json", new TypeReference<>() {
        });
    }

    public DevResumeFixture resume(String fileName) {
        return readJson(resumePath(fileName), new TypeReference<>() {
        });
    }

    public String scriptedReply(String stageName, int replyIndex) {
        DevStageRepliesFixture fixture = readJson("demo/stage-replies.json", new TypeReference<>() {
        });
        List<String> replies = fixture.replies().get(stageName);
        if (replies == null || replies.isEmpty()) {
            throw BusinessException.badRequest("dev fixture 阶段回复未配置");
        }
        if (replyIndex >= replies.size()) {
            return "";
        }
        int index = Math.max(0, replyIndex);
        return replies.get(index);
    }

    public String report(String targetPosition) {
        if (FRONTEND_POSITION_NAME.equals(targetPosition)) {
            return frontendReport();
        }
        if (ALGORITHM_POSITION_NAME.equals(targetPosition)) {
            return algorithmReport();
        }
        String template = readText("demo/report-template.md");
        return template.replace("{{position}}", targetPosition == null ? "目标岗位" : targetPosition);
    }

    public List<DevWeaknessFixture> weaknesses() {
        return readJson("demo/weaknesses.json", new TypeReference<>() {
        });
    }

    public List<QnaPair> javaScript() {
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

    public List<QnaPair> frontendScript() {
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

    public List<QnaPair> algorithmScript() {
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

    public ScoreSeed javaScore() {
        return new ScoreSeed(8, 9, 8);
    }

    public ScoreSeed frontendScore() {
        return new ScoreSeed(7, 7, 7);
    }

    public ScoreSeed algorithmScore() {
        return new ScoreSeed(5, 6, 6);
    }

    public List<WeaknessSeed> javaStorylineWeaknesses() {
        return List.of(
            new WeaknessSeed("千亿级并发架构瓶颈", "对于跨数据中心的强一致性容灾方案及底层 Paxos 选主细节掌握不够纯熟。")
        );
    }

    public List<WeaknessSeed> frontendStorylineWeaknesses() {
        return List.of(
            new WeaknessSeed("WebRTC 底层信令协商", "能应用 WebRTC，但在穿透 NAT/Firewall (STUN/TURN) 时的 ICE 候选收集原理上解释含糊。"),
            new WeaknessSeed("复杂状态抽象", "面对多实例子应用的 Pinia 状态隔离机制没有给出完美的防污染方案。")
        );
    }

    public List<WeaknessSeed> algorithmStorylineWeaknesses() {
        return List.of(
            new WeaknessSeed("分布式训练通信瓶颈", "未经历过真实的多机多卡环境，对 Ring AllReduce 机制和显存梯度累积原理完全陌生。"),
            new WeaknessSeed("线上问题排查", "特征漂移和线上指标断崖式下跌时的降级排查策略过于理论化，缺乏生产实操经验。"),
            new WeaknessSeed("评估指标局限", "过分迷信 AUC 等离线指标，对在线 A/B 实验的置信度检验和流量正交不了解。")
        );
    }

    public String javaOngoingUserAnswer() {
        return "为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。";
    }

    private String resumePath(String fileName) {
        if (fileName != null) {
            if (fileName.contains("Java")) {
                return "demo/resume_java.json";
            }
            if (fileName.contains("前端")) {
                return "demo/resume_frontend.json";
            }
            if (fileName.contains("算法")) {
                return "demo/resume_algorithm.json";
            }
        }
        return "demo/resume_java.json";
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

    private String readText(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取 dev fixture 夹具失败: " + path);
        }
    }

    private <T> T readJson(String path, TypeReference<T> typeReference) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取 dev fixture 夹具失败: " + path);
        }
    }

    public record DevLlmConfigFixture(String providerKey, String model, String apiKeyMasked) {
    }

    public record DevResumeFixture(
        List<String> skills,
        List<ResumeProjectDto> projects,
        String rawText
    ) {
    }

    private record DevStageRepliesFixture(Map<String, List<String>> replies) {
    }

    public record DevWeaknessFixture(String category, String description) {
    }

    public record QnaPair(String stageName, String systemPrompt, String aiQuestion, String userAnswer) {
    }

    public record ScoreSeed(int technical, int expression, int logic) {
    }

    public record WeaknessSeed(String category, String description) {
    }
}
