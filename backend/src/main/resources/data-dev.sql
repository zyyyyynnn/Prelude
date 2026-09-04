-- Local/dev acceptance data only.
-- Restored from the last complete dev fixture before commit 6957e871a0dd871abb29c383e89da82bce1391a4.
-- Content is preserved from Git history; only table/column mappings target the current schema.

INSERT INTO `user_account` (
  `username`, `password_hash`, `email`, `theme_preference`, `revision`
) VALUES (
  'demo',
  '$argon2id$v=19$m=19456,t=2,p=1$lqr0JdEZ6e08TWKuAGS5OA$Q2d2jojUIcR4eNImjp3ez2SIlQ/7cZvbr4mtJm6duxM',
  'demo@example.com',
  'system',
  0
) AS new
ON DUPLICATE KEY UPDATE
  `password_hash` = new.`password_hash`,
  `email` = new.`email`;

SET @demo_account_id = (
  SELECT `id` FROM `user_account` WHERE `username` = 'demo' LIMIT 1
);

-- Dev reset mirrors the historical fixture service: replace the demo account's sessions and resumes.
DELETE rc FROM `retrieval_chunk` rc
INNER JOIN `interview_session` s ON s.`id` = rc.`scope_id`
WHERE rc.`scope_type` = 'session' AND s.`account_id` = @demo_account_id;
DELETE aw FROM `account_weakness` aw
INNER JOIN `interview_session` s ON s.`id` = aw.`session_id`
WHERE s.`account_id` = @demo_account_id;
DELETE sh FROM `score_history` sh
INNER JOIN `interview_session` s ON s.`id` = sh.`session_id`
WHERE s.`account_id` = @demo_account_id;
DELETE ist FROM `interview_stage` ist
INNER JOIN `interview_session` s ON s.`id` = ist.`session_id`
WHERE s.`account_id` = @demo_account_id;
DELETE im FROM `interview_message` im
INNER JOIN `interview_session` s ON s.`id` = im.`session_id`
WHERE s.`account_id` = @demo_account_id;
DELETE FROM `interview_session` WHERE `account_id` = @demo_account_id;
DELETE FROM `model_execution_snapshot` WHERE `account_id` = @demo_account_id;
DELETE FROM `resume` WHERE `account_id` = @demo_account_id;

INSERT INTO `model_profile` (
  `account_id`, `provider`, `model`, `credential_id`, `custom_endpoint_url`,
  `reasoning_level`, `effective_parameters_json`, `model_capability_json`,
  `fallback_capabilities_json`, `created_at`, `updated_at`
) VALUES (
  @demo_account_id, 'deepseek', 'deepseek-v4-pro', NULL, NULL,
  'AUTO', '{"maxOutputTokens":4096}', NULL, '[]',
  '2026-04-18 15:15:00', '2026-04-18 15:15:00'
) AS new
ON DUPLICATE KEY UPDATE
  `provider` = new.`provider`,
  `model` = new.`model`,
  `credential_id` = new.`credential_id`,
  `custom_endpoint_url` = new.`custom_endpoint_url`,
  `reasoning_level` = new.`reasoning_level`,
  `effective_parameters_json` = new.`effective_parameters_json`,
  `model_capability_json` = new.`model_capability_json`,
  `fallback_capabilities_json` = new.`fallback_capabilities_json`;

SET @demo_profile_id = (
  SELECT `id` FROM `model_profile` WHERE `account_id` = @demo_account_id LIMIT 1
);

INSERT INTO `resume` (
  `account_id`, `file_name`, `raw_text`, `parsed_skills`, `parsed_projects`, `created_at`
) VALUES (
  @demo_account_id, 'Java高级架构.pdf', '张高级
Java 资深架构师方向
技能：Java, Spring Cloud, MySQL, Redis, RocketMQ, Kubernetes
项目一：高并发电商秒杀系统，负责核心链路架构，Redis 预扣减、RocketMQ 异步落库、Redisson 分布式锁解决超卖，抗住10万并发。
项目二：全局分布式账本网关，负责熔断限流配置与慢查询调优，引入 ShardingSphere 实现分库分表。',
  '["Java","Spring Cloud","MySQL","Redis","RocketMQ","Kubernetes"]', '[{"name":"高并发电商秒杀系统","description":"基于 Spring Cloud 搭建微服务架构。采用 Redis 预扣减库存与 RocketMQ 异步削峰，抗住 10 万 QPS 并发。使用 Lua 脚本保证库存扣减原子性，基于 Redisson 实现分布式锁防止超卖。"},{"name":"全局分布式账本网关","description":"基于 Resilience4j 实现网关层熔断与限流，优化慢 SQL 查询，将 P99 延迟从 800ms 降至 150ms。基于分库分表（ShardingSphere）解决单表亿级数据存储瓶颈。"}]', '2026-04-22 16:40:00'
);
SET @resume_java_id = LAST_INSERT_ID();

INSERT INTO `resume` (
  `account_id`, `file_name`, `raw_text`, `parsed_skills`, `parsed_projects`, `created_at`
) VALUES (
  @demo_account_id, '大前端资深开发.pdf', '李资深
大前端架构师方向
技能：Vue 3, TypeScript, Vite, Pinia, Node.js, WebRTC
项目一：自研微前端中后台框架，基于 qiankun 隔离子应用，落地虚拟列表解决10万条数据渲染瓶颈。
项目二：实时在线视频面试系统，基于 WebRTC 与 WebSocket 搭建流式双向通信，通过重排音频流切片解决丢包杂音。',
  '["Vue 3","TypeScript","Vite","Pinia","Node.js","WebRTC"]', '[{"name":"自研微前端中后台框架","description":"基于 Vue 3 + TypeScript 构建，通过 qiankun 实现子应用隔离。重构海量数据 Table 组件的虚拟列表，解决 10万级 数据渲染卡顿问题。"},{"name":"实时在线视频面试系统","description":"基于 WebRTC 与 WebSocket 实现低延迟音视频通话与实时协同白板。深度优化 Web Audio API 处理音频切片，解决网络抖动带来的杂音与丢包问题。"}]', '2026-04-20 16:10:00'
);
SET @resume_frontend_id = LAST_INSERT_ID();

INSERT INTO `resume` (
  `account_id`, `file_name`, `raw_text`, `parsed_skills`, `parsed_projects`, `created_at`
) VALUES (
  @demo_account_id, '推荐算法工程师.pdf', '王专家
推荐算法与大模型方向
技能：Python, PyTorch, TensorFlow, Spark, Faiss, LLM Fine-tuning
项目一：百亿级跨域推荐系统，主导召回与排序迭代，优化 DeepFM 网络结构，基于 Faiss 实现毫秒级特征检索。
项目二：垂直领域大语言模型微调，采用 LoRA 路线微调 Llama-3，通过高质量语料与对齐策略显著降低幻觉并提升专业评估准确度。',
  '["Python","PyTorch","TensorFlow","Spark","Faiss","LLM Fine-tuning"]', '[{"name":"百亿级跨域推荐系统","description":"主导推荐链路召回与排序模型（DeepFM/DIN）迭代。使用 Spark 处理百亿级日志提取特征，通过 Faiss 搭建向量检索系统，提升点击率 12%。"},{"name":"垂直领域大语言模型微调","description":"基于 Llama-3 进行指令微调（LoRA）。构建高质量垂直域指令集，优化 RLHF 对齐策略，降低模型在专业知识问答上的幻觉率约 30%。"}]', '2026-04-18 15:30:00'
);
SET @resume_algorithm_id = LAST_INSERT_ID();

SET @position_java_id = (SELECT `id` FROM `position_template` WHERE `name` = 'Java 后端工程师' LIMIT 1);
SET @position_frontend_id = (SELECT `id` FROM `position_template` WHERE `name` = '前端工程师' LIMIT 1);
SET @position_algorithm_id = (SELECT `id` FROM `position_template` WHERE `name` = '算法工程师' LIMIT 1);

SET @model_capability_json = '{"provider":"deepseek","model":"deepseek-v4-pro","reasoning":true,"structuredOutput":true,"toolCalling":true,"streaming":true,"vision":false,"multilingual":true,"longContext":true,"embedding":false,"nativeRealtimeVoice":false,"supportedReasoningLevels":["AUTO","LOW","HIGH","MAX"]}';

INSERT INTO `model_execution_snapshot` (
  `account_id`, `profile_id`, `provider`, `model`, `reasoning_level`,
  `effective_parameters_json`, `capability_version`, `model_capability_json`,
  `fallback_capabilities_json`, `credential_id`, `custom_endpoint_url`, `created_at`
) VALUES (
  @demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO',
  '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-04-23 14:00:00'
);
SET @demo_snapshot_id = LAST_INSERT_ID();

INSERT INTO `interview_session` (
  `account_id`, `resume_id`, `position_id`, `target_position`,
  `model_execution_snapshot_id`, `status`, `summary`, `summary_report`, `jd_text`, `created_at`
) VALUES (
  @demo_account_id, @resume_java_id, @position_java_id, 'Java 后端工程师',
  @demo_snapshot_id, 'ongoing', NULL, NULL, NULL, '2026-04-23 14:00:00'
);
SET @session_ongoing_id = LAST_INSERT_ID();

INSERT INTO `interview_stage` (`session_id`, `stage_name`, `started_at`, `ended_at`) VALUES
  (@session_ongoing_id, 'warmup', '2026-04-23 14:00:00', '2026-04-23 14:12:00'),
  (@session_ongoing_id, 'technical', '2026-04-23 14:12:00', NULL);
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT @session_ongoing_id, 'system', `system_prompt`, 0, NULL, NULL, '2026-04-23 14:00:00'
FROM `position_template` WHERE `id` = @position_java_id;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) VALUES
  (@session_ongoing_id, 'assistant', '你好，我看你简历上写了负责高并发电商秒杀系统。我们单刀直入，在流量洪峰打过来的时候，你的 Redis 预扣减是怎么保证原子性的？', 1, NULL, NULL, '2026-04-23 14:01:00'),
  (@session_ongoing_id, 'user', '为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。', 2, 7, '开场表现自然，对过往项目描述清晰。', '2026-04-23 14:04:00'),
  (@session_ongoing_id, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', 3, NULL, NULL, '2026-04-23 14:12:00'),
  (@session_ongoing_id, 'assistant', '既然提到了通过 RocketMQ 异步削峰落库。如果 Redis 扣减成功，但丢给 RocketMQ 的 Half Message 发送超时了，你怎么收场？', 4, NULL, NULL, '2026-04-23 14:13:00');

INSERT INTO `model_execution_snapshot` (
  `account_id`, `profile_id`, `provider`, `model`, `reasoning_level`,
  `effective_parameters_json`, `capability_version`, `model_capability_json`,
  `fallback_capabilities_json`, `credential_id`, `custom_endpoint_url`, `created_at`
) VALUES (
  @demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO',
  '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-04-22 10:00:00'
);
SET @demo_snapshot_id = LAST_INSERT_ID();

INSERT INTO `interview_session` (
  `account_id`, `resume_id`, `position_id`, `target_position`,
  `model_execution_snapshot_id`, `status`, `summary`, `summary_report`, `jd_text`, `created_at`
) VALUES (
  @demo_account_id, @resume_java_id, @position_java_id, 'Java 后端工程师',
  @demo_snapshot_id, 'finished', NULL, '{"summary":{"fitAssessment":"候选人具备可继续深入面试的基础能力，项目表达较完整。","actionRecommendation":"具备继续进入下一轮技术评估的基础。","overallRisk":"JVM、并发、连接管理、数据库优化与异常边界仍需补强。"},"scores":{"technical":7,"expression":8,"logic":7,"overall":7.4},"stagePerformances":[{"stageName":"warmup","score":7.5,"summary":"能够围绕项目链路说明实现思路。","positiveSignals":["回答结构较完整，表达较清晰"],"negativeSignals":[],"improvementSuggestions":["继续保持项目背景与个人职责的清晰表达"]},{"stageName":"technical","score":7.5,"summary":"对常见后端技术栈有较稳定的实践经验。","positiveSignals":["能够说明接口、鉴权与会话记录的实现思路"],"negativeSignals":["数据库优化与缓存策略缺少量化依据"],"improvementSuggestions":["在数据库优化与缓存策略上给出更具体的量化依据"]},{"stageName":"deep_dive","score":7.5,"summary":"并发与连接管理的底层细节仍需加强。","positiveSignals":["能够围绕异常场景继续推导"],"negativeSignals":["JVM、并发与连接管理的细节表达不足"],"improvementSuggestions":["补强 JVM、并发与连接管理的细节表达"]},{"stageName":"closing","score":7,"summary":"整体表现稳定。","positiveSignals":["具备继续评估的基础"],"negativeSignals":["边界条件说明仍可更严谨"],"improvementSuggestions":["对异常场景、幂等控制和边界条件说明更加严谨"]}],"questionReviews":[{"stageName":"warmup","question":"你好，我看你简历上写了负责高并发电商秒杀系统。我们单刀直入，在流量洪峰打过来的时候，你的 Redis 预扣减是怎么保证原子性的？","answerSummary":"为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。","score":7,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"继续保持项目背景与个人职责的清晰表达"},{"stageName":"warmup","question":"使用 Lua 脚本确实能保证单机原子性。但如果秒杀库存分片在了多个 Redis 节点上，单个 Lua 脚本还能搞定吗？","answerSummary":"如果是集群架构，我们会通过 Hash Tag 将同一个商品的所有库存分片路由到同一个 Redis 节点，这样 Lua 脚本依然可以保证局部原子性。如果要做全局库存聚合，则会利用 Redisson 的分布式锁做更粗粒度的控制。","score":8,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"继续保持项目背景与个人职责的清晰表达"},{"stageName":"technical","question":"既然提到了通过 RocketMQ 异步削峰落库。如果 Redis 扣减成功，但丢给 RocketMQ 的 Half Message 发送超时了，你怎么收场？","answerSummary":"这时候其实本地事务还没有提交。如果 Half Message 超时，RocketMQ 会主动回调我们的 TransactionCheckListener。我们在回调中检查 Redis 的流水状态或者本地的消息防重表，来决定是 Rollback 还是 Commit。同时前端会有轮询机制做最终确认。","score":7,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"在数据库优化与缓存策略上给出更具体的量化依据"},{"stageName":"technical","question":"本地消息表方案确实兜底了最终一致性。但考虑到大促期间本地数据库 TPS 本来就面临极限，如果每笔失败的消息都强行落库，数据库岂不是会当场宕机？","answerSummary":"这是一个非常好的点。我们在生产中并不会每次都去查表。首先我们会做 MQ 的自动重试机制；其次，对于确实需要补偿的，我们把消息表放在独立的补偿库，而不是核心交易库；或者改用日志解析（如 Canal）去对比数据差异，将对核心库的侵入降到最低。","score":8,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"在数据库优化与缓存策略上给出更具体的量化依据"},{"stageName":"deep_dive","question":"那我们再往深挖一步。在 Redisson 实现的分布式锁防超卖环节，如果你拿到锁的微服务节点突然发生了长时间的 Full GC（STW），导致 WatchDog 看门狗未能续期锁被释放，被其他节点抢走，等 STW 结束这个微服务继续执行，引发了超卖。这种情况你怎么防御？","answerSummary":"如果发生这种极端 STW 导致的锁失效，单纯依赖锁是不够的。我们会在数据库层加一个 `update stock = stock - 1 where id = ? and stock > 0` 的乐观锁兜底。即使 STW 后继续执行，SQL 执行时会发现库存不足而回滚，保证绝对不会超卖。","score":7,"scoringReason":"逻辑推导合理，但在底层机制的理解上还有提升空间。","improvementSuggestion":"补强 JVM、并发与连接管理的细节表达"},{"stageName":"deep_dive","question":"采用乐观锁或者在数据库层做行级悲观锁兜底是一个思路。但在分库分表（ShardingSphere）的场景下，跨节点的行锁往往会退化为分布式事务，极大拉低吞吐量，这又怎么取舍？","answerSummary":"在追求极致吞吐的大促场景下，我们会尽量避免分布式事务。我们的做法是将库存拆分成更细的维度（比如按照用户 ID 路由到特定库），或者干脆不保证严格一致，而是采用基于流水表的异步核对系统（T+1 或者延迟对账），通过后续发放补偿券来对冲极小概率的超卖损失，因为这种工程取舍在商业上是完全可接受的。","score":8,"scoringReason":"逻辑推导合理，但在底层机制的理解上还有提升空间。","improvementSuggestion":"补强 JVM、并发与连接管理的细节表达"},{"stageName":"closing","question":"好的，最后一个问题。抛开技术方案，如果这次双十一秒杀让你重新从头设计，在预算不变的情况下，你会优先在哪个环节做架构降级或减负？","answerSummary":"我会优先在查询链路上做彻底的降级。通过在 CDN 或者边缘节点（甚至客户端本地缓存）做静态化，把绝大部分查询流量在到达网关前拦截。对于后端的核心服务，只保留扣库存这一个极简接口，剥离掉所有非核心的营销规则计算，把服务器算力真正用到刀刃上。","score":7,"scoringReason":"收尾陈述简明扼要，整体沟通顺畅。","improvementSuggestion":"对异常场景、幂等控制和边界条件说明更加严谨"}],"strengths":["能够围绕项目链路说明接口、鉴权与会话记录的实现思路","对常见后端技术栈有较稳定的实践经验","回答结构较完整，表达较清晰"],"weaknesses":["千亿级并发架构瓶颈：对于跨数据中心的强一致性容灾方案及底层 Paxos 选主细节掌握不够纯熟。"],"trainingPlan":{"threeDay":["补强 JVM、并发与连接管理的细节表达"],"sevenDay":["在数据库优化与缓存策略上给出更具体的量化依据"],"nextInterviewFocus":["异常场景、幂等控制和边界条件"]},"finalAdvice":"整体表现稳定，具备继续进入下一轮技术评估的基础。","markdownFallback":"# 面试评估报告\\n\\n## 面试概览\\n- 目标岗位：Java 后端工程师\\n- 结论：候选人具备可继续深入面试的基础能力，项目表达较完整。\\n\\n## 三维评分\\n- 技术能力：7/10\\n- 表达清晰度：8/10\\n- 逻辑思维：7/10\\n\\n## 优势总结\\n- 能够围绕项目链路说明接口、鉴权与会话记录的实现思路\\n- 对常见后端技术栈有较稳定的实践经验\\n- 回答结构较完整，表达较清晰\\n\\n## 改进建议\\n1. 补强 JVM、并发与连接管理的细节表达\\n2. 在数据库优化与缓存策略上给出更具体的量化依据\\n3. 对异常场景、幂等控制和边界条件说明可以更严谨\\n\\n## 总结\\n整体表现稳定，具备继续进入下一轮技术评估的基础。","resumeImprovements":[]}', NULL, '2026-04-22 10:00:00'
);
SET @session_java_id = LAST_INSERT_ID();

INSERT INTO `interview_stage` (`session_id`, `stage_name`, `started_at`, `ended_at`) VALUES
  (@session_java_id, 'warmup', '2026-04-22 10:00:00', '2026-04-22 10:08:00'),
  (@session_java_id, 'technical', '2026-04-22 10:08:00', '2026-04-22 10:18:00'),
  (@session_java_id, 'deep_dive', '2026-04-22 10:18:00', '2026-04-22 10:28:00'),
  (@session_java_id, 'closing', '2026-04-22 10:28:00', '2026-04-22 10:34:00');
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT @session_java_id, 'system', `system_prompt`, 0, NULL, NULL, '2026-04-22 10:00:00'
FROM `position_template` WHERE `id` = @position_java_id;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) VALUES
  (@session_java_id, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', 1, NULL, NULL, '2026-04-22 10:00:05'),
  (@session_java_id, 'assistant', '你好，我看你简历上写了负责高并发电商秒杀系统。我们单刀直入，在流量洪峰打过来的时候，你的 Redis 预扣减是怎么保证原子性的？', 2, NULL, NULL, '2026-04-22 10:01:00'),
  (@session_java_id, 'user', '为了抗住秒杀瞬间的 10 万并发，我们核心采用了 Redis + Lua 脚本进行预扣减。由于 Lua 脚本在 Redis 内部是单线程执行的，所以能够天然保证扣减判断与操作的原子性。扣减成功后，再异步投递事务消息给 RocketMQ 让 MySQL 慢慢消化。', 3, 7, '开场表现自然，对过往项目描述清晰。', '2026-04-22 10:02:00'),
  (@session_java_id, 'assistant', '使用 Lua 脚本确实能保证单机原子性。但如果秒杀库存分片在了多个 Redis 节点上，单个 Lua 脚本还能搞定吗？', 4, NULL, NULL, '2026-04-22 10:03:00'),
  (@session_java_id, 'user', '如果是集群架构，我们会通过 Hash Tag 将同一个商品的所有库存分片路由到同一个 Redis 节点，这样 Lua 脚本依然可以保证局部原子性。如果要做全局库存聚合，则会利用 Redisson 的分布式锁做更粗粒度的控制。', 5, 8, '开场表现自然，对过往项目描述清晰。', '2026-04-22 10:04:00'),
  (@session_java_id, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', 6, NULL, NULL, '2026-04-22 10:08:05'),
  (@session_java_id, 'assistant', '既然提到了通过 RocketMQ 异步削峰落库。如果 Redis 扣减成功，但丢给 RocketMQ 的 Half Message 发送超时了，你怎么收场？', 7, NULL, NULL, '2026-04-22 10:09:00'),
  (@session_java_id, 'user', '这时候其实本地事务还没有提交。如果 Half Message 超时，RocketMQ 会主动回调我们的 TransactionCheckListener。我们在回调中检查 Redis 的流水状态或者本地的消息防重表，来决定是 Rollback 还是 Commit。同时前端会有轮询机制做最终确认。', 8, 7, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-22 10:10:00'),
  (@session_java_id, 'assistant', '本地消息表方案确实兜底了最终一致性。但考虑到大促期间本地数据库 TPS 本来就面临极限，如果每笔失败的消息都强行落库，数据库岂不是会当场宕机？', 9, NULL, NULL, '2026-04-22 10:11:00'),
  (@session_java_id, 'user', '这是一个非常好的点。我们在生产中并不会每次都去查表。首先我们会做 MQ 的自动重试机制；其次，对于确实需要补偿的，我们把消息表放在独立的补偿库，而不是核心交易库；或者改用日志解析（如 Canal）去对比数据差异，将对核心库的侵入降到最低。', 10, 8, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-22 10:12:00'),
  (@session_java_id, 'assistant', '那我们再往深挖一步。在 Redisson 实现的分布式锁防超卖环节，如果你拿到锁的微服务节点突然发生了长时间的 Full GC（STW），导致 WatchDog 看门狗未能续期锁被释放，被其他节点抢走，等 STW 结束这个微服务继续执行，引发了超卖。这种情况你怎么防御？', 11, NULL, NULL, '2026-04-22 10:19:00'),
  (@session_java_id, 'user', '如果发生这种极端 STW 导致的锁失效，单纯依赖锁是不够的。我们会在数据库层加一个 `update stock = stock - 1 where id = ? and stock > 0` 的乐观锁兜底。即使 STW 后继续执行，SQL 执行时会发现库存不足而回滚，保证绝对不会超卖。', 12, 7, '逻辑推导合理，但在底层机制的理解上还有提升空间。', '2026-04-22 10:20:00'),
  (@session_java_id, 'assistant', '采用乐观锁或者在数据库层做行级悲观锁兜底是一个思路。但在分库分表（ShardingSphere）的场景下，跨节点的行锁往往会退化为分布式事务，极大拉低吞吐量，这又怎么取舍？', 13, NULL, NULL, '2026-04-22 10:21:00'),
  (@session_java_id, 'user', '在追求极致吞吐的大促场景下，我们会尽量避免分布式事务。我们的做法是将库存拆分成更细的维度（比如按照用户 ID 路由到特定库），或者干脆不保证严格一致，而是采用基于流水表的异步核对系统（T+1 或者延迟对账），通过后续发放补偿券来对冲极小概率的超卖损失，因为这种工程取舍在商业上是完全可接受的。', 14, 8, '逻辑推导合理，但在底层机制的理解上还有提升空间。', '2026-04-22 10:22:00'),
  (@session_java_id, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', 15, NULL, NULL, '2026-04-22 10:28:05'),
  (@session_java_id, 'assistant', '好的，最后一个问题。抛开技术方案，如果这次双十一秒杀让你重新从头设计，在预算不变的情况下，你会优先在哪个环节做架构降级或减负？', 16, NULL, NULL, '2026-04-22 10:29:00'),
  (@session_java_id, 'user', '我会优先在查询链路上做彻底的降级。通过在 CDN 或者边缘节点（甚至客户端本地缓存）做静态化，把绝大部分查询流量在到达网关前拦截。对于后端的核心服务，只保留扣库存这一个极简接口，剥离掉所有非核心的营销规则计算，把服务器算力真正用到刀刃上。', 17, 7, '收尾陈述简明扼要，整体沟通顺畅。', '2026-04-22 10:30:00');
INSERT INTO `score_history` (
  `account_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`
) VALUES (
  @demo_account_id, @session_java_id, 7, 8, 7, '2026-04-22 10:35:00'
);
INSERT INTO `account_weakness` (`account_id`, `session_id`, `category`, `description`, `created_at`) VALUES
  (@demo_account_id, @session_java_id, '千亿级并发架构瓶颈', '对于跨数据中心的强一致性容灾方案及底层 Paxos 选主细节掌握不够纯熟。', '2026-04-22 10:36:00');

INSERT INTO `model_execution_snapshot` (
  `account_id`, `profile_id`, `provider`, `model`, `reasoning_level`,
  `effective_parameters_json`, `capability_version`, `model_capability_json`,
  `fallback_capabilities_json`, `credential_id`, `custom_endpoint_url`, `created_at`
) VALUES (
  @demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO',
  '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-04-20 16:10:00'
);
SET @demo_snapshot_id = LAST_INSERT_ID();

INSERT INTO `interview_session` (
  `account_id`, `resume_id`, `position_id`, `target_position`,
  `model_execution_snapshot_id`, `status`, `summary`, `summary_report`, `jd_text`, `created_at`
) VALUES (
  @demo_account_id, @resume_frontend_id, @position_frontend_id, '前端工程师',
  @demo_snapshot_id, 'finished', NULL, '{"summary":{"fitAssessment":"候选人具备较完整的前端工程化和页面性能意识，适合继续深入评估。","actionRecommendation":"具备继续进入前端专项面试的基础。","overallRisk":"性能量化、复杂状态边界、移动端适配与无障碍说明仍需加强。"},"scores":{"technical":8,"expression":7,"logic":7,"overall":7.3},"stagePerformances":[{"stageName":"warmup","score":7.5,"summary":"能够围绕组件拆分和状态管理说明实现思路。","positiveSignals":["能将交互细节与真实使用体验关联起来"],"negativeSignals":[],"improvementSuggestions":["保持项目背景与职责边界的清晰表达"]},{"stageName":"technical","score":7.5,"summary":"具备前端工程化和页面性能意识。","positiveSignals":["对性能排查、接口耗时和渲染边界有基本判断能力"],"negativeSignals":["浏览器性能链路缺少量化说明"],"improvementSuggestions":["补强浏览器性能指标、资源加载和渲染链路的量化说明"]},{"stageName":"deep_dive","score":7,"summary":"复杂组件状态归属和复用边界仍需更清晰。","positiveSignals":["能围绕页面链路继续分析"],"negativeSignals":["复杂状态取舍说明不够完整"],"improvementSuggestions":["在复杂组件状态归属和复用边界上给出更清晰的取舍"]},{"stageName":"closing","score":7,"summary":"整体表现稳定。","positiveSignals":["具备继续进入专项面试的基础"],"negativeSignals":["移动端和无障碍状态说明仍可更完整"],"improvementSuggestions":["完整说明移动端适配、键盘焦点和无障碍状态"]}],"questionReviews":[{"stageName":"warmup","question":"你好，我看你在简历中主导了微前端的落地和海量数据的虚拟列表重构。先说微前端，基于 qiankun 做子应用隔离时，你如何解决由于不同团队的技术栈和版本不同导致的全局 CSS 污染和 JS 变量冲突？","answerSummary":"对于 JS 变量，qiankun 内部利用 Proxy 实现了浏览器的 JS 沙箱隔离，这能挡掉大部分全局污染。但对于 CSS，原生的 strictStyleIsolation 会带来挂载在 body 上的弹窗组件样式丢失问题。我们最终采用了 experimentalStyleIsolation 为 CSS 规则动态增加前缀，结合 BEM 命名规范和 CSS…","score":7,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"保持项目背景与职责边界的清晰表达"},{"stageName":"warmup","question":"面对多实例子应用的 Pinia 状态隔离机制，你有没有遇到过子应用切换时状态泄漏的问题？你们是如何防污染的？","answerSummary":"遇到过。Pinia 默认是单例模式挂载在 Vue App 实例上的，子应用卸载时如果不主动清理，状态仍会驻留在内存。我们在子应用的 unmount 生命周期中，主动调用 $reset() 重置所有核心 store，并配合 qiankun 的生命周期钩子，确保子应用的状态生命周期与微前端框架严格绑定。","score":8,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"保持项目背景与职责边界的清晰表达"},{"stageName":"technical","question":"来聊聊 10 万级数据的虚拟列表。当用户快速滚动（比如鼠标拖拽滚动条）时，经常会出现白屏或者掉帧闪烁。你认为这是纯粹的 DOM 渲染瓶颈，还是计算层面的问题？怎么解决？","answerSummary":"两者都有。快速滚动时，一方面是大量的可见数据需要重新计算起始索引（startIndex）和偏移量，另一方面是反复的 DOM 替换和重绘。为了解决白屏，我们并不是只渲染可视区域，而是上下各自额外缓冲了一屏数据。同时，把滚动事件的监听通过 requestAnimationFrame 做节流，把高度计算放到 Web Worker 中进行，避免阻塞主线程。","score":7,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"补强浏览器性能指标、资源加载和渲染链路的量化说明"},{"stageName":"technical","question":"对于高度不固定的长列表，你们在 ResizeObserver 回调中更新高度如果引发重排（Reflow），会导致严重的阻塞。你们是怎么避免循环触发布局更新的？","answerSummary":"高度不确定的虚拟列表确实是最难搞的。我们采用的是预估高度 + 异步修正的策略。初始给一个默认预估高度渲染出滚动条，然后在 DOM 渲染后利用 ResizeObserver 获取真实高度更新缓存数组。为了避免重排引发的卡顿，我们将高度修正的操作批量推迟到了 nextTick 或者下一次 rAF 帧中合并执行，绝不在当前渲染帧里强制读取 offsetHeight…","score":8,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"补强浏览器性能指标、资源加载和渲染链路的量化说明"},{"stageName":"deep_dive","question":"在实时在线视频面试系统中，你在简历上写到用 Web Audio API 重排音频切片解决网络抖动。如果客户端的网络发生严重的丢包（10%以上丢包率），单纯的重排根本无用，你会如何在前端层面做平滑处理？","answerSummary":"如果在传输层我们走的是基于 UDP 的 WebRTC（RTP/RTCP协议），对于弱网我们会在信令握手时开启前向纠错（FEC）或者重传机制（NACK）。如果是基于 WebSocket 收到的分包，在前端 AudioWorklet 接收时如果发现 seq 不连续，我们会在缓冲区加入短时的静音包或者利用算法做基于时域的音频拉伸（Time Stretching）来…","score":7,"scoringReason":"逻辑推导合理，但在底层机制的理解上还有提升空间。","improvementSuggestion":"在复杂组件状态归属和复用边界上给出更清晰的取舍"},{"stageName":"closing","question":"如果现在要将整个微前端框架和在线面试系统打包迁移到鸿蒙原生或者桌面端 Electron，你觉得前端现有的架构设计中，阻力最大的是哪一部分？","answerSummary":"阻力最大的肯定是平台底层 API 的差异。例如 WebRTC 那些多媒体 API 和微前端依赖的 iframe / Proxy 沙箱，在鸿蒙的 ArkUI 或者某些严格受限的 Webview 里根本不兼容。我们需要把底层的网络通信和音视频采集抽象出一层适配器接口（Adapter），不同平台注入不同的实现，前端业务代码只依赖这层 Adapter，这样才能低成本…","score":7,"scoringReason":"收尾陈述简明扼要，整体沟通顺畅。","improvementSuggestion":"完整说明移动端适配、键盘焦点和无障碍状态"}],"strengths":["能够围绕组件拆分、状态管理和页面链路说明实现思路","对性能排查、接口耗时和渲染边界有基本判断能力","能将交互细节与真实使用体验关联起来"],"weaknesses":["WebRTC 底层信令协商：能应用 WebRTC，但在穿透 NAT/Firewall (STUN/TURN) 时的 ICE 候选收集原理上解释含糊。","复杂状态抽象：面对多实例子应用的 Pinia 状态隔离机制没有给出完美的防污染方案。"],"trainingPlan":{"threeDay":["补强浏览器性能指标、资源加载和渲染链路的量化说明"],"sevenDay":["梳理复杂组件状态归属和复用边界"],"nextInterviewFocus":["移动端适配、键盘焦点和无障碍状态"]},"finalAdvice":"整体表现稳定，具备继续进入前端专项面试的基础。","markdownFallback":"# 面试评估报告\\n\\n## 面试概览\\n- 目标岗位：前端工程师\\n- 结论：候选人具备较完整的前端工程化和页面性能意识，适合继续深入评估。\\n\\n## 三维评分\\n- 技术能力：8/10\\n- 表达清晰度：7/10\\n- 逻辑思维：7/10\\n\\n## 优势总结\\n- 能够围绕组件拆分、状态管理和页面链路说明实现思路\\n- 对性能排查、接口耗时和渲染边界有基本判断能力\\n- 能将交互细节与真实使用体验关联起来\\n\\n## 改进建议\\n1. 补强浏览器性能指标、资源加载和渲染链路的量化说明\\n2. 在复杂组件状态归属和复用边界上给出更清晰的取舍\\n3. 对移动端适配、键盘焦点和无障碍状态说明可以更完整\\n\\n## 总结\\n整体表现稳定，具备继续进入前端专项面试的基础。","resumeImprovements":[]}', NULL, '2026-04-20 16:10:00'
);
SET @session_frontend_id = LAST_INSERT_ID();

INSERT INTO `interview_stage` (`session_id`, `stage_name`, `started_at`, `ended_at`) VALUES
  (@session_frontend_id, 'warmup', '2026-04-20 16:10:00', '2026-04-20 16:18:00'),
  (@session_frontend_id, 'technical', '2026-04-20 16:18:00', '2026-04-20 16:28:00'),
  (@session_frontend_id, 'deep_dive', '2026-04-20 16:28:00', '2026-04-20 16:38:00'),
  (@session_frontend_id, 'closing', '2026-04-20 16:38:00', '2026-04-20 16:44:00');
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT @session_frontend_id, 'system', `system_prompt`, 0, NULL, NULL, '2026-04-20 16:10:00'
FROM `position_template` WHERE `id` = @position_frontend_id;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) VALUES
  (@session_frontend_id, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', 1, NULL, NULL, '2026-04-20 16:10:05'),
  (@session_frontend_id, 'assistant', '你好，我看你在简历中主导了微前端的落地和海量数据的虚拟列表重构。先说微前端，基于 qiankun 做子应用隔离时，你如何解决由于不同团队的技术栈和版本不同导致的全局 CSS 污染和 JS 变量冲突？', 2, NULL, NULL, '2026-04-20 16:11:00'),
  (@session_frontend_id, 'user', '对于 JS 变量，qiankun 内部利用 Proxy 实现了浏览器的 JS 沙箱隔离，这能挡掉大部分全局污染。但对于 CSS，原生的 strictStyleIsolation 会带来挂载在 body 上的弹窗组件样式丢失问题。我们最终采用了 experimentalStyleIsolation 为 CSS 规则动态增加前缀，结合 BEM 命名规范和 CSS Modules 彻底解决了样式冲突。', 3, 7, '开场表现自然，对过往项目描述清晰。', '2026-04-20 16:12:00'),
  (@session_frontend_id, 'assistant', '面对多实例子应用的 Pinia 状态隔离机制，你有没有遇到过子应用切换时状态泄漏的问题？你们是如何防污染的？', 4, NULL, NULL, '2026-04-20 16:13:00'),
  (@session_frontend_id, 'user', '遇到过。Pinia 默认是单例模式挂载在 Vue App 实例上的，子应用卸载时如果不主动清理，状态仍会驻留在内存。我们在子应用的 unmount 生命周期中，主动调用 $reset() 重置所有核心 store，并配合 qiankun 的生命周期钩子，确保子应用的状态生命周期与微前端框架严格绑定。', 5, 8, '开场表现自然，对过往项目描述清晰。', '2026-04-20 16:14:00'),
  (@session_frontend_id, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', 6, NULL, NULL, '2026-04-20 16:18:05'),
  (@session_frontend_id, 'assistant', '来聊聊 10 万级数据的虚拟列表。当用户快速滚动（比如鼠标拖拽滚动条）时，经常会出现白屏或者掉帧闪烁。你认为这是纯粹的 DOM 渲染瓶颈，还是计算层面的问题？怎么解决？', 7, NULL, NULL, '2026-04-20 16:19:00'),
  (@session_frontend_id, 'user', '两者都有。快速滚动时，一方面是大量的可见数据需要重新计算起始索引（startIndex）和偏移量，另一方面是反复的 DOM 替换和重绘。为了解决白屏，我们并不是只渲染可视区域，而是上下各自额外缓冲了一屏数据。同时，把滚动事件的监听通过 requestAnimationFrame 做节流，把高度计算放到 Web Worker 中进行，避免阻塞主线程。', 8, 7, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-20 16:20:00'),
  (@session_frontend_id, 'assistant', '对于高度不固定的长列表，你们在 ResizeObserver 回调中更新高度如果引发重排（Reflow），会导致严重的阻塞。你们是怎么避免循环触发布局更新的？', 9, NULL, NULL, '2026-04-20 16:21:00'),
  (@session_frontend_id, 'user', '高度不确定的虚拟列表确实是最难搞的。我们采用的是预估高度 + 异步修正的策略。初始给一个默认预估高度渲染出滚动条，然后在 DOM 渲染后利用 ResizeObserver 获取真实高度更新缓存数组。为了避免重排引发的卡顿，我们将高度修正的操作批量推迟到了 nextTick 或者下一次 rAF 帧中合并执行，绝不在当前渲染帧里强制读取 offsetHeight 引发同步重排。', 10, 8, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-20 16:22:00'),
  (@session_frontend_id, 'assistant', '在实时在线视频面试系统中，你在简历上写到用 Web Audio API 重排音频切片解决网络抖动。如果客户端的网络发生严重的丢包（10%以上丢包率），单纯的重排根本无用，你会如何在前端层面做平滑处理？', 11, NULL, NULL, '2026-04-20 16:29:00'),
  (@session_frontend_id, 'user', '如果在传输层我们走的是基于 UDP 的 WebRTC（RTP/RTCP协议），对于弱网我们会在信令握手时开启前向纠错（FEC）或者重传机制（NACK）。如果是基于 WebSocket 收到的分包，在前端 AudioWorklet 接收时如果发现 seq 不连续，我们会在缓冲区加入短时的静音包或者利用算法做基于时域的音频拉伸（Time Stretching）来平滑过渡，实在严重时则直接降级为文字模式。', 12, 7, '逻辑推导合理，但在底层机制的理解上还有提升空间。', '2026-04-20 16:30:00'),
  (@session_frontend_id, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', 13, NULL, NULL, '2026-04-20 16:38:05'),
  (@session_frontend_id, 'assistant', '如果现在要将整个微前端框架和在线面试系统打包迁移到鸿蒙原生或者桌面端 Electron，你觉得前端现有的架构设计中，阻力最大的是哪一部分？', 14, NULL, NULL, '2026-04-20 16:39:00'),
  (@session_frontend_id, 'user', '阻力最大的肯定是平台底层 API 的差异。例如 WebRTC 那些多媒体 API 和微前端依赖的 iframe / Proxy 沙箱，在鸿蒙的 ArkUI 或者某些严格受限的 Webview 里根本不兼容。我们需要把底层的网络通信和音视频采集抽象出一层适配器接口（Adapter），不同平台注入不同的实现，前端业务代码只依赖这层 Adapter，这样才能低成本迁移。', 15, 7, '收尾陈述简明扼要，整体沟通顺畅。', '2026-04-20 16:40:00');
INSERT INTO `score_history` (
  `account_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`
) VALUES (
  @demo_account_id, @session_frontend_id, 8, 7, 7, '2026-04-20 16:45:00'
);
INSERT INTO `account_weakness` (`account_id`, `session_id`, `category`, `description`, `created_at`) VALUES
  (@demo_account_id, @session_frontend_id, 'WebRTC 底层信令协商', '能应用 WebRTC，但在穿透 NAT/Firewall (STUN/TURN) 时的 ICE 候选收集原理上解释含糊。', '2026-04-20 16:46:00'),
  (@demo_account_id, @session_frontend_id, '复杂状态抽象', '面对多实例子应用的 Pinia 状态隔离机制没有给出完美的防污染方案。', '2026-04-20 16:47:00');

INSERT INTO `model_execution_snapshot` (
  `account_id`, `profile_id`, `provider`, `model`, `reasoning_level`,
  `effective_parameters_json`, `capability_version`, `model_capability_json`,
  `fallback_capabilities_json`, `credential_id`, `custom_endpoint_url`, `created_at`
) VALUES (
  @demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO',
  '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-04-18 15:30:00'
);
SET @demo_snapshot_id = LAST_INSERT_ID();

INSERT INTO `interview_session` (
  `account_id`, `resume_id`, `position_id`, `target_position`,
  `model_execution_snapshot_id`, `status`, `summary`, `summary_report`, `jd_text`, `created_at`
) VALUES (
  @demo_account_id, @resume_algorithm_id, @position_algorithm_id, '算法工程师',
  @demo_snapshot_id, 'finished', NULL, '{"summary":{"fitAssessment":"候选人能按数据、模型和评估链路组织回答，但实验复现和误差分析仍需加强。","actionRecommendation":"具备算法岗继续评估的基础。","overallRisk":"实验细节、复杂度量化与失败样本复盘仍需提高。"},"scores":{"technical":7,"expression":6,"logic":8,"overall":7.3},"stagePerformances":[{"stageName":"warmup","score":7.5,"summary":"能够从样本、特征、基线方案和指标口径拆解问题。","positiveSignals":["回答结构较清楚"],"negativeSignals":[],"improvementSuggestions":["继续保持数据、模型与评估链路的结构化表达"]},{"stageName":"technical","score":7.5,"summary":"具备离线评估和线上差异的基本排查路径。","positiveSignals":["能说明数据分布变化带来的影响"],"negativeSignals":["复杂度和边界规模缺少量化"],"improvementSuggestions":["补强时间复杂度、空间复杂度和边界规模的量化表达"]},{"stageName":"deep_dive","score":7,"summary":"实验复现和误差分析仍需加强。","positiveSignals":["能够围绕指标选择继续推导"],"negativeSignals":["验证集划分和误差分析示例不够具体"],"improvementSuggestions":["在验证集划分、误差分析和指标选择上给出更具体示例"]},{"stageName":"closing","score":7,"summary":"整体具备继续评估的基础。","positiveSignals":["能够总结数据分布和线上表现差异"],"negativeSignals":["实验记录和失败样本复盘不够严谨"],"improvementSuggestions":["严谨说明实验版本、参数记录和失败样本复盘"]}],"questionReviews":[{"stageName":"warmup","question":"你好，我看你负责过百亿级推荐系统的迭代。传统双塔结构在召回阶段最大的痛点是它的交互仅限于最终 Embedding 的内积，缺乏细粒度的特征交叉。这个问题你们是怎么缓解的？","answerSummary":"是的，双塔模型为了保证 Faiss 的海量毫秒级检索，强行将 User 和 Item 在顶层才做点积，导致底层的细粒度特征完全没有交互。我们的缓解思路是在粗排阶段补强特征交叉。我们引入了类似 SENet 或 DCN（Deep & Cross Network）的轻量级网络对召回结果做快速打分，在不显著增加线上耗时的前提下把强交叉信息补了回来。","score":7,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"继续保持数据、模型与评估链路的结构化表达"},{"stageName":"warmup","question":"在多目标优化的排序模型中（比如同时预估点击率和转化率的 ESSM 模型），你是如何处理样本空间偏置（SSB）和数据稀疏（DS）问题的？","answerSummary":"针对样本空间偏置（SSB），我们使用了全样本空间进行训练，利用 CTR 的预估值作为 CVR 预估的权重或者直接构建 CVR 的条件概率模型（如 ESSM），使得 CVR 模型能看到所有曝光样本而不是仅仅点击样本。对于数据稀疏（DS），我们通过多任务学习（MTL）共享底层 Embedding 表，让稀疏目标的特征从高频目标的丰富样本中获得更新信号，从而缓解冷…","score":8,"scoringReason":"开场表现自然，对过往项目描述清晰。","improvementSuggestion":"继续保持数据、模型与评估链路的结构化表达"},{"stageName":"technical","question":"我们转到大语言模型微调。你提到使用 LoRA 微调 Llama-3。在多机多卡的分布式训练中，如果卡间通信成为了瓶颈，特别是在反向传播更新梯度时，你对 Ring AllReduce 机制和显存优化策略有实战经验吗？","answerSummary":"LoRA 本质是低秩自适应，参数量较小，通常不需要极致的并行。但如果我们做全参微调或是千亿级模型，确实需要面对通信瓶颈。对于显存优化，我们会使用 ZeRO-2 或 ZeRO-3 将优化器状态和梯度分片到不同的 GPU 上；对于通信瓶颈，我们会采用梯度累积减少 AllReduce 频次，并在物理拓扑上利用 NVLink 保证机内通信带宽，跨机时利用 Infin…","score":7,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"补强时间复杂度、空间复杂度和边界规模的量化表达"},{"stageName":"technical","question":"在减少垂直领域专业知识问答幻觉时，你提到优化了 RLHF 对齐策略。RLHF 中如果奖励模型（Reward Model）的评分出现过度优化（Reward Hacking），即模型学会了迎合打分机制却不解决实际问题，你怎么应对？","answerSummary":"为了解决 Reward Hacking，我们引入了 KL 散度（Kullback-Leibler divergence）惩罚项。在 PPO 阶段，我们会限制当前 Policy 模型生成的分布不要偏离初始 SFT（Supervised Fine-Tuning）模型太远。同时我们在训练期间会定期让人类标注员（Human-in-the-loop）介入，抽样检查高分…","score":8,"scoringReason":"技术概念阐述清晰，若能结合具体代码实现则更佳。","improvementSuggestion":"补强时间复杂度、空间复杂度和边界规模的量化表达"},{"stageName":"deep_dive","question":"线上问题排查方面，如果有天早上你们发现线上的点击率指标断崖式下跌，但服务本身的 QPS 和错误率完全正常。你作为推荐算法工程师，第一时间会怎么排查？","answerSummary":"如果服务层没问题，我会立刻排查数据分布和特征漂移。首先我会去对比最近几小时的实时日志和昨天同期的特征覆盖率，看是不是某些关键字段（比如用户年龄、设备类型）的数据源断流导致被大量填了默认值。其次，我会看下游大推池里的物品分布有没有发生突变（比如某个热点新闻引发的马太效应）。最后，检查前一天晚上是否有过模型重训或特征工程上线的动作导致的隐式 Bug。","score":7,"scoringReason":"逻辑推导合理，但在底层机制的理解上还有提升空间。","improvementSuggestion":"在验证集划分、误差分析和指标选择上给出更具体示例"},{"stageName":"closing","question":"最后，你在评估离线模型时，有没有遇到过离线 AUC 上涨，但线上 A/B 实验反而收益为负的情况？过分迷信 AUC 等离线指标会有什么问题？","answerSummary":"非常常见。离线 AUC 的提升很多时候仅仅是因为模型学会了利用了某个强漏斗特征，或者受到了 Position Bias（位置偏见）的影响，导致离线数据评估看起来很美。线上我们不仅看 CTR，还要看用户留存和商业变现（如 RPM），这些目标很多时候是互相制约的。我们在 A/B 实验时，必须严格遵守流量正交和显著性检验（p-value < 0.05），避免将短期…","score":7,"scoringReason":"收尾陈述简明扼要，整体沟通顺畅。","improvementSuggestion":"严谨说明实验版本、参数记录和失败样本复盘"}],"strengths":["能够从样本、特征、基线方案和指标口径拆解问题","对离线评估和线上表现差异有基本排查路径","回答结构较清楚，能说明数据分布变化带来的影响"],"weaknesses":["分布式训练通信瓶颈：未经历过真实的多机多卡环境，对 Ring AllReduce 机制和显存梯度累积原理完全陌生。","线上问题排查：特征漂移和线上指标断崖式下跌时的降级排查策略过于理论化，缺乏生产实操经验。","评估指标局限：过分迷信 AUC 等离线指标，对在线 A/B 实验的置信度检验和流量正交不了解。"],"trainingPlan":{"threeDay":["补强时间复杂度、空间复杂度和边界规模的量化表达"],"sevenDay":["训练验证集划分、误差分析和指标选择"],"nextInterviewFocus":["实验版本、参数记录和失败样本复盘"]},"finalAdvice":"整体具备算法岗继续评估的基础，但需要提高实验细节和表达稳定性。","markdownFallback":"# 面试评估报告\\n\\n## 面试概览\\n- 目标岗位：算法工程师\\n- 结论：候选人能按数据、模型和评估链路组织回答，但实验复现和误差分析仍需加强。\\n\\n## 三维评分\\n- 技术能力：7/10\\n- 表达清晰度：6/10\\n- 逻辑思维：8/10\\n\\n## 优势总结\\n- 能够从样本、特征、基线方案和指标口径拆解问题\\n- 对离线评估和线上表现差异有基本排查路径\\n- 回答结构较清楚，能说明数据分布变化带来的影响\\n\\n## 改进建议\\n1. 补强时间复杂度、空间复杂度和边界规模的量化表达\\n2. 在验证集划分、误差分析和指标选择上给出更具体示例\\n3. 对实验版本、参数记录和失败样本复盘说明可以更严谨\\n\\n## 总结\\n整体具备算法岗继续评估的基础，但需要提高实验细节和表达稳定性。","resumeImprovements":[]}', NULL, '2026-04-18 15:30:00'
);
SET @session_algorithm_id = LAST_INSERT_ID();

INSERT INTO `interview_stage` (`session_id`, `stage_name`, `started_at`, `ended_at`) VALUES
  (@session_algorithm_id, 'warmup', '2026-04-18 15:30:00', '2026-04-18 15:38:00'),
  (@session_algorithm_id, 'technical', '2026-04-18 15:38:00', '2026-04-18 15:48:00'),
  (@session_algorithm_id, 'deep_dive', '2026-04-18 15:48:00', '2026-04-18 15:58:00'),
  (@session_algorithm_id, 'closing', '2026-04-18 15:58:00', '2026-04-18 16:04:00');
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT @session_algorithm_id, 'system', `system_prompt`, 0, NULL, NULL, '2026-04-18 15:30:00'
FROM `position_template` WHERE `id` = @position_algorithm_id;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) VALUES
  (@session_algorithm_id, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', 1, NULL, NULL, '2026-04-18 15:30:05'),
  (@session_algorithm_id, 'assistant', '你好，我看你负责过百亿级推荐系统的迭代。传统双塔结构在召回阶段最大的痛点是它的交互仅限于最终 Embedding 的内积，缺乏细粒度的特征交叉。这个问题你们是怎么缓解的？', 2, NULL, NULL, '2026-04-18 15:31:00'),
  (@session_algorithm_id, 'user', '是的，双塔模型为了保证 Faiss 的海量毫秒级检索，强行将 User 和 Item 在顶层才做点积，导致底层的细粒度特征完全没有交互。我们的缓解思路是在粗排阶段补强特征交叉。我们引入了类似 SENet 或 DCN（Deep & Cross Network）的轻量级网络对召回结果做快速打分，在不显著增加线上耗时的前提下把强交叉信息补了回来。', 3, 7, '开场表现自然，对过往项目描述清晰。', '2026-04-18 15:32:00'),
  (@session_algorithm_id, 'assistant', '在多目标优化的排序模型中（比如同时预估点击率和转化率的 ESSM 模型），你是如何处理样本空间偏置（SSB）和数据稀疏（DS）问题的？', 4, NULL, NULL, '2026-04-18 15:33:00'),
  (@session_algorithm_id, 'user', '针对样本空间偏置（SSB），我们使用了全样本空间进行训练，利用 CTR 的预估值作为 CVR 预估的权重或者直接构建 CVR 的条件概率模型（如 ESSM），使得 CVR 模型能看到所有曝光样本而不是仅仅点击样本。对于数据稀疏（DS），我们通过多任务学习（MTL）共享底层 Embedding 表，让稀疏目标的特征从高频目标的丰富样本中获得更新信号，从而缓解冷启动问题。', 5, 8, '开场表现自然，对过往项目描述清晰。', '2026-04-18 15:34:00'),
  (@session_algorithm_id, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', 6, NULL, NULL, '2026-04-18 15:38:05'),
  (@session_algorithm_id, 'assistant', '我们转到大语言模型微调。你提到使用 LoRA 微调 Llama-3。在多机多卡的分布式训练中，如果卡间通信成为了瓶颈，特别是在反向传播更新梯度时，你对 Ring AllReduce 机制和显存优化策略有实战经验吗？', 7, NULL, NULL, '2026-04-18 15:39:00'),
  (@session_algorithm_id, 'user', 'LoRA 本质是低秩自适应，参数量较小，通常不需要极致的并行。但如果我们做全参微调或是千亿级模型，确实需要面对通信瓶颈。对于显存优化，我们会使用 ZeRO-2 或 ZeRO-3 将优化器状态和梯度分片到不同的 GPU 上；对于通信瓶颈，我们会采用梯度累积减少 AllReduce 频次，并在物理拓扑上利用 NVLink 保证机内通信带宽，跨机时利用 InfiniBand 结合 Ring AllReduce 甚至 Tree AllReduce 来降低网络延迟。', 8, 7, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-18 15:40:00'),
  (@session_algorithm_id, 'assistant', '在减少垂直领域专业知识问答幻觉时，你提到优化了 RLHF 对齐策略。RLHF 中如果奖励模型（Reward Model）的评分出现过度优化（Reward Hacking），即模型学会了迎合打分机制却不解决实际问题，你怎么应对？', 9, NULL, NULL, '2026-04-18 15:41:00'),
  (@session_algorithm_id, 'user', '为了解决 Reward Hacking，我们引入了 KL 散度（Kullback-Leibler divergence）惩罚项。在 PPO 阶段，我们会限制当前 Policy 模型生成的分布不要偏离初始 SFT（Supervised Fine-Tuning）模型太远。同时我们在训练期间会定期让人类标注员（Human-in-the-loop）介入，抽样检查高分回复是否真实，并动态调整或回退奖励模型的训练集。', 10, 8, '技术概念阐述清晰，若能结合具体代码实现则更佳。', '2026-04-18 15:42:00'),
  (@session_algorithm_id, 'assistant', '线上问题排查方面，如果有天早上你们发现线上的点击率指标断崖式下跌，但服务本身的 QPS 和错误率完全正常。你作为推荐算法工程师，第一时间会怎么排查？', 11, NULL, NULL, '2026-04-18 15:49:00'),
  (@session_algorithm_id, 'user', '如果服务层没问题，我会立刻排查数据分布和特征漂移。首先我会去对比最近几小时的实时日志和昨天同期的特征覆盖率，看是不是某些关键字段（比如用户年龄、设备类型）的数据源断流导致被大量填了默认值。其次，我会看下游大推池里的物品分布有没有发生突变（比如某个热点新闻引发的马太效应）。最后，检查前一天晚上是否有过模型重训或特征工程上线的动作导致的隐式 Bug。', 12, 7, '逻辑推导合理，但在底层机制的理解上还有提升空间。', '2026-04-18 15:50:00'),
  (@session_algorithm_id, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', 13, NULL, NULL, '2026-04-18 15:58:05'),
  (@session_algorithm_id, 'assistant', '最后，你在评估离线模型时，有没有遇到过离线 AUC 上涨，但线上 A/B 实验反而收益为负的情况？过分迷信 AUC 等离线指标会有什么问题？', 14, NULL, NULL, '2026-04-18 15:59:00'),
  (@session_algorithm_id, 'user', '非常常见。离线 AUC 的提升很多时候仅仅是因为模型学会了利用了某个强漏斗特征，或者受到了 Position Bias（位置偏见）的影响，导致离线数据评估看起来很美。线上我们不仅看 CTR，还要看用户留存和商业变现（如 RPM），这些目标很多时候是互相制约的。我们在 A/B 实验时，必须严格遵守流量正交和显著性检验（p-value < 0.05），避免将短期波动当作长期收益发布上线。', 15, 7, '收尾陈述简明扼要，整体沟通顺畅。', '2026-04-18 16:00:00');
INSERT INTO `score_history` (
  `account_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`
) VALUES (
  @demo_account_id, @session_algorithm_id, 7, 6, 8, '2026-04-18 16:05:00'
);
INSERT INTO `account_weakness` (`account_id`, `session_id`, `category`, `description`, `created_at`) VALUES
  (@demo_account_id, @session_algorithm_id, '分布式训练通信瓶颈', '未经历过真实的多机多卡环境，对 Ring AllReduce 机制和显存梯度累积原理完全陌生。', '2026-04-18 16:06:00'),
  (@demo_account_id, @session_algorithm_id, '线上问题排查', '特征漂移和线上指标断崖式下跌时的降级排查策略过于理论化，缺乏生产实操经验。', '2026-04-18 16:07:00'),
  (@demo_account_id, @session_algorithm_id, '评估指标局限', '过分迷信 AUC 等离线指标，对在线 A/B 实验的置信度检验和流量正交不了解。', '2026-04-18 16:08:00');
