# Residual Risk Register

本台账只保留当前仍需跟踪的工程风险，以及已关闭风险的索引。详细依赖解释不在 active 文档中长期展开，避免旧风险持续制造噪音。

## 当前观察项

### R-008 — application 边界仍有过渡依赖 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Current status | P2 结构债务 |
| Location | `insight.application.InsightQueryService`、`insight.application.ReportGenerateHandler` |
| Current behavior | `InsightQueryService` 仍返回 insight API DTO；`ReportGenerateHandler` 仍直接依赖 `JobExecutionStore` 基础设施类型 |
| Current mitigation | 面试模块已通过 Command/Result/View 与架构测试完成同类边界收敛；论文证据明确标注当前例外 |
| Trade-off | 不影响现有功能，但会扩大 application 对适配层和基础设施的编译依赖 |
| Safe fix | 为 insight 查询引入 application View；为作业状态读写引入 application/platform Port，并由基础设施实现 |

#### Next review condition

- 修改 insight 查询响应或异步报告重试逻辑时同步处理。
- 新增全局 application 边界测试前处理现有例外。

### R-007 — 简历文档启动回填随历史数据量增长 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Current status | P7 观察项 |
| Location | `resume.application.BackfillResumeDocuments`、`prelude.resume.backfill-on-startup` |
| Current behavior | 启动后按主键游标分批回填空 `document_json`；单行失败继续，输出 total/succeeded/failed/skipped/successRate |
| Current mitigation | 全部新列可空；回填幂等且原子只更新空文档；可用配置关闭启动执行，读取失败继续 fallback `raw_text` |
| Trade-off | 历史简历量很大时会增加启动后数据库负载；畸形旧 JSON 会降低迁移成功率但不阻断应用启动 |
| Safe fix | 数据量达到需限速级别时迁为受控 Job Runtime 任务，保留现有 Port、游标和统计模型 |

#### Next review condition

- 历史简历达到万级，或回填耗时/数据库负载影响启动 SLO。
- 日志出现 `failed > 0`，或 document fallback 指标持续非零。

### R-006 — Local Realtime Hub 仅保证单实例扇出 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Current status | P5 观察项 |
| Location | `platform.realtime.LocalRealtimeHub`、`prelude.realtime.mode=local` |
| Current behavior | SSE 连接与 session fan-out 已统一经 `RealtimePort`，默认实现仍为进程内连接表 |
| Current mitigation | 单实例部署行为完整；Port 与 connection/sink 契约已隔离 transport，SSE 事件名保持兼容 |
| Trade-off | 多实例且无 sticky session 时，报告事件可能发布到未持有目标连接的实例 |
| Safe fix | 增加 Redis pub/sub `RealtimePort` adapter，或在生产入口强制 sticky session 并明确容量限制 |

#### Next review condition

- 后端部署副本数大于 1。
- 报告 ready/fallback 事件出现跨实例丢失迹象。

### R-005 — 异步任务补投依赖数据库轮询 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Current status | P4 观察项 |
| Location | `platform.job.PendingJobRecoveryPublisher`、`async_job` |
| Current behavior | enqueue 先写 `async_job` 再投 RabbitMQ；pending 超时任务按固定周期补投，consumer 以原子 claim、最大尝试次数和运行租约吸收重复/崩溃恢复 |
| Current mitigation | jobId/idempotencyKey 唯一约束、状态查询、3 次有限重试、running 租约回收；上游错误不通过 API 暴露 |
| Trade-off | 补投延迟受轮询周期影响；数据库不可用时调度与恢复同时暂停 |
| Safe fix | 任务量或 SLO 提升后迁移到事务 outbox + publisher confirm/CDC，保留现有 Job Port 与幂等状态机 |

#### Next review condition

- 报告任务量明显增长，或对 pending 时长建立生产 SLO。
- 出现数据库/MQ 分区导致的补投延迟告警。

### R-004 — Retrieval 重建依赖 Embedding 可用性 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Current status | P3 观察项 |
| Location | `platform.retrieval.InMemoryRetrievalAdapter`、`retrieval_chunk` |
| Current behavior | 文本 chunk 持久化，向量为每实例内存缓存；进程重启后可从 chunk 重建 |
| Current mitigation | source/persisted 双重建路径、结构化日志；Embedding 失败返回空检索结果，不阻断面试 |
| Trade-off | Embedding 服务不可用期间无法恢复向量检索，相关上下文增强暂时缺失 |
| Safe fix | 引入可版本化的持久化 embedding 或共享向量存储，并保留 content hash 失效策略 |

#### Next review condition

- 多实例部署或开始对检索命中率设定生产 SLO。
- Embedding 服务故障导致面试追问质量出现可观察下降。

### R-003 — TTS 池跨 session 串行吞吐 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Recorded in | `588bf73` |
| Current status | P3 观察项 |
| Location | `bootstrap.ThreadPoolConfig#ttsTaskExecutor`、`interview.api.voice.VoiceInterviewTurnService` |
| Current behavior | 全局 `ttsTaskExecutor` 为 single-thread FIFO；同一 turn 内可保证 sentence 与 `sink.audio` 顺序一致 |
| Current mitigation | 30s timeout、`ttsFailed` / `ttsTimedOut` 双 flag、`sink.audio` 前二次检查、单元测试覆盖顺序/失败/timeout |
| Trade-off | 跨 session 语音合成会串行，吞吐不是最优 |
| Safe fix | session-level queue：每 session 串行、跨 session 并行 |

#### 不直接放大全局线程池的原因

直接把 `ttsTaskExecutor` 改为多线程会让同一 turn 的多个 sentence 并行合成，`sink.audio(base64)` 可能乱序推送，用户听到的语音顺序会错位。正确方案是引入 session-level 串行调度，而不是粗暴调大全局池。

#### Next review condition

- 真实多用户语音场景出现 TTS 延迟、卡顿或排队反馈。
- 准备正式推进语音端到端能力，而不只是文字主链路与语音容错测试。

## 已关闭风险索引

| ID | 风险 | 首次登记 | 关闭提交 | 当前状态 | 复核条件 |
| --- | --- | --- | --- | --- | --- |
| R-001 | `form-data` CRLF injection (high) | `b39eee1` | `588bf73` | `frontend/package.json#overrides.form-data = 4.0.6`，`npm audit --omit=dev` 为 0 | axios 升级后评估是否可移除 overrides；若出现新 high/critical advisory，重新登记 |
| R-002 | `dompurify` 配置污染 / bypass (moderate) | `b39eee1` | `588bf73` | `frontend/package.json#overrides.dompurify = 3.4.11`，`npm audit --omit=dev` 为 0 | jspdf 升级后评估是否可移除 overrides；若改用 `jsPDF.html()`，重新评估触发路径 |

## 维护规则

- 只登记仍影响后续开发决策的风险。
- 已关闭风险只保留索引、关闭路径和复核条件。
- 不把 npm advisory 缓解说明写成“绝对安全”声明。
- 新增依赖、语音调度、LLM fallback 或异步任务可靠性变化时，优先复核本台账。
