# Residual Risk Register

本台账只保留会影响当前维护决策的限制。状态为“观察”不等于故障；触发复核条件前不引入额外基础设施。

## 当前观察项

### R-009 — serious 级对比度问题尚未进入设计复核

| 字段 | 内容 |
| --- | --- |
| Current status | P2 设计与可访问性债务 |
| Location | 现有品牌色/token 在部分浅色与暗色组合中的 axe `color-contrast` 结果 |
| Current behavior | `verify:a11y` 阻断 critical；serious 结果保留在测试输出，不宣称 WCAG 2 AA 完整合规 |
| Current mitigation | Tooltip 使用统一中性反相表面；键盘、焦点、语义和 critical 问题继续 blocking；视觉改动必须通过 `DESIGN.md` 复核 |
| Trade-off | 本轮不以临时改色破坏既定视觉与 token 基线 |
| Safe fix | 作为独立设计评审确定受影响的语义 token 和跨主题对比度，再同步更新视觉基线 |
| Next review | 进入正式无障碍验收，或用户批准 token 级视觉调整时 |

### R-007 — 简历文档启动回填随历史数据量增长

| 字段 | 内容 |
| --- | --- |
| Current status | P3 运行观察 |
| Location | `resume.application.BackfillResumeDocuments`、`ResumeDocumentBackfillRunner`、`prelude.resume.backfill-on-startup` |
| Current behavior | 启动时经 `resumeBackfillExecutor` 异步调度按主键游标分批回填空 `document_json`；单行失败继续，读取仍可退回 `raw_text` |
| Current mitigation | 新列可空、回填幂等、只更新空文档，可配置关闭，输出成功/失败统计；启动线程不再同步阻塞于整批回填（`ResumeDocumentBackfillRunnerTest`） |
| Trade-off | 万级历史数据仍可能在启动后增加数据库负载；尚未迁入受控 Job Runtime |
| Safe fix | 达到规模阈值后迁入受控 Job Runtime，保留现有 Port、游标和统计模型 |
| Next review | 历史简历达到万级、影响启动 SLO，或失败数持续非零 |

### R-006 — Realtime 默认仍是单实例扇出；多实例需显式切换

| 字段 | 内容 |
| --- | --- |
| Current status | P2 部署边界 |
| Location | `LocalRealtimeHub`（`prelude.realtime.mode=local`，默认）、`RedisRealtimeHub`（`mode=redis`） |
| Current behavior | SSE 连接与 session fan-out 经 `RealtimePort`；默认连接表在进程内；`mode=redis` 时本地投递后经 Redis pub/sub 跨实例广播 |
| Current mitigation | Port 隔离 + `RealtimeConnectionRegistry` 共享连接表；`RedisRealtimeHub` / `RedisRealtimeHubTest` 覆盖本地投递、对端入站、本实例回环过滤 |
| Trade-off | 默认 `local` 在多实例且无 sticky session 时仍可能丢跨实例事件；Redis 模式要求 Redis 可用且部署显式切换 |
| Safe fix | 副本数 > 1 时将 `prelude.realtime.mode` 设为 `redis`，或在入口强制 sticky session；生产前做跨实例联调 |
| Next review | 后端副本数大于 1，或出现跨实例事件缺失 |

### R-005 — 异步任务补投依赖数据库轮询

| 字段 | 内容 |
| --- | --- |
| Current status | P2 可靠性边界 |
| Location | `platform.job.RabbitJobScheduler`、`PendingJobRecoveryPublisher`、`async_job` |
| Current behavior | enqueue 先写 DB 再投 MQ；MQ 发布失败保持 PENDING（记录脱敏 `lastError`，不置 FAILED）；`dispatchedAt` 为空的 PENDING 可被客户端重试立即再投，或由轮询补投超时 PENDING / 租约过期 RUNNING；consumer 原子 claim、最多 3 次尝试 |
| Current mitigation | idempotencyKey 唯一、状态可查、重复投递可吸收、错误落库前截断脱敏；`RabbitJobSchedulerTest` 覆盖失败保 PENDING 与未投递重试 |
| Trade-off | 补投延迟仍受轮询周期影响；数据库不可用时调度与恢复同时暂停；尚无事务 outbox / publisher confirm |
| Safe fix | 建立生产投递 SLO 后迁移到事务 outbox + publisher confirm/CDC |
| Next review | 任务量显著增长、建立 pending SLO，或出现 DB/MQ 分区告警 |

### R-004 — Retrieval 仍是单实例线性候选扫描

| 字段 | 内容 |
| --- | --- |
| Current status | P2 容量与质量边界 |
| Location | `platform.retrieval.InMemoryRetrievalAdapter`、`retrieval_chunk` |
| Current behavior | chunk 和带模型版本的 embedding 持久化；查询融合完整候选的向量/关键词分数，Embedding 故障退化到关键词 |
| Current mitigation | 64 个 scope 锁条带、内容哈希、原子快照替换、重启恢复测试、5,000 chunk 合成容量实验与 Recall@5 断言；小型标注查询夹具 `HybridRetrievalAnnotatedQueriesTest`（简历域 3 条期望片段） |
| Trade-off | 标注集仍小且为合成语料，不代表真实语义相关性；每次搜索复杂度仍为 O(N) |
| Safe fix | 扩充人工标注真实查询集；规模或 P95 超限后引入近似向量索引/共享存储 |
| Next review | 单 scope 明显超过 5,000 chunk、多实例部署，或开始设定检索质量/延迟 SLO |

## 已关闭风险

| ID | 风险 | 关闭证据 | 重新打开条件 |
| --- | --- | --- | --- |
| R-003 | TTS 池跨 session 全局串行 | `SessionKeyedSerialExecutor`（session 内 FIFO、session 间可并行）、`ThreadPoolConfig#ttsTaskExecutor` 池化、`VoiceInterviewTurnService#submitTtsTask`、`SessionKeyedSerialExecutorTest` | 回退为全局单线程池，或去掉 per-session 串行导致同 turn 乱序 |
| R-008 | insight application 依赖 API DTO / Job infrastructure | application View、`JobExecutionPort`、`ArchitectureBoundaryTest` | application 再次导入 API DTO 或 infrastructure |
| R-002 | `dompurify` 配置污染 / bypass advisory | `frontend/package.json` override + `npm audit --omit=dev` | 移除 override、升级 PDF 链路或出现新 advisory |
| R-001 | `form-data` CRLF injection advisory | `frontend/package.json` override + `npm audit --omit=dev` | 移除 override、axios 依赖变化或出现新 advisory |

## 维护规则

- 不把测试或缓解措施表述为绝对安全、零丢失或生产级保证。
- 风险关闭必须有当前代码、测试、CI 或数据证据。
- 新增多实例部署、语音容量、Provider 协议、依赖 override 或异步投递 SLO 时优先复核本台账。
