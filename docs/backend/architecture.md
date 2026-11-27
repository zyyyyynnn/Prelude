# 后端架构

## Composition Root

| 项         | 契约                                                         |
| ---------- | ------------------------------------------------------------ |
| 唯一装配根 | `com.prelude.PreludeApplication`                             |
| 模块发现   | Spring Modulith 从根包发现 Application Modules               |
| 拓扑验证   | `ApplicationModules.verify()` 验证模块环、内部访问与显式依赖 |

## Application Modules

```text
identity  settings  llm       tools
context   agent     artifact  assets
jobs      resume    position  documents
interview voice     activity  telemetry
```

| 模块        | 运行时职责                                     | 对外面                                           |
| ----------- | ---------------------------------------------- | ------------------------------------------------ |
| `identity`  | 账户、认证、Profile 与账户隔离                 | `identity::api`、`identity::security`            |
| `settings`  | 账号级设置聚合边界                             | 路线图占位，见下文                               |
| `llm`       | 模型路由、BYOK、执行快照、Embed 与语音模型接入 | `llm::api`                                       |
| `tools`     | Tool 治理与 MCP 边界                           | 路线图占位，见下文                               |
| `context`   | 混合检索、chunk 持久化与索引重建               | 根包 `RetrievalPort`                             |
| `agent`     | LangGraph4j 执行与人机协同边界                 | 路线图占位，见下文                               |
| `artifact`  | 训练报告、Analytics 与正式成果发布             | 模块内 `ArtifactCommandApi` / `ArtifactQueryApi` |
| `assets`    | Asset、附件、对象存储与耐久删除                | `assets::integration`                            |
| `jobs`      | 耐久后台任务、租约与事件发布恢复               | `jobs::integration`                              |
| `resume`    | PDF 导入、解析与简历上下文投影                 | `resume::integration`                            |
| `position`  | 内置岗位与用户自定义岗位                       | `position::catalog`                              |
| `documents` | 文档识别、受限解析与文本提取                   | `documents::extraction`                          |
| `interview` | 面试会话、消息、阶段、评估与报告入口           | `interview::integration`                         |
| `voice`     | 语音识别、回答合成与语音回合编排               | 经 `interview` 集成端口驱动会话                  |
| `activity`  | SSE 连接与实时发布订阅                         | 根包 `RealtimePort`                              |
| `telemetry` | 使用量与运行时遥测归属                         | 路线图占位，见下文                               |

### 可见性与依赖

| 规则             | 契约                                                                                                                     |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------ |
| 默认可见面       | 模块根包；`application`、`domain`、`infrastructure`、`web` 不对外可见                                                    |
| 具名接口         | 跨模块协作使用 `@NamedInterface`；普通内部类不提升为接口                                                                 |
| 根包例外         | `activity` 的 `RealtimePort`、`context` 的 `RetrievalPort` 直接位于根包，由 `artifact` 与 `interview` 按裸模块名授权引用 |
| `interview` 仓储 | `interview/application/repository` 不导出；跨模块只经 `interview/application/port/InterviewSessionGuard` 获取会话摘要    |
| `interview` 领域 | `interview/domain` 不导出；跨模块只经 `interview/api/port` 与 `interview/application/port` 的快照投影读取和驱动面试      |
| HTTP 适配器      | 控制器位于模块 `web` 包；`HealthController` 与 `GlobalExceptionHandler` 是根包全局横切                                   |
| Port 用途        | 隔离外部基础设施、框架或跨模块接口；无边界收益的普通内部类直接表达职责                                                   |

九个具名接口及其声明包：

| 具名接口                 | 声明包                                                                        |
| ------------------------ | ----------------------------------------------------------------------------- |
| `identity::api`          | `identity/api`                                                                |
| `identity::security`     | `identity/api/security`                                                       |
| `llm::api`               | `llm/api`                                                                     |
| `assets::integration`    | `assets/api`                                                                  |
| `documents::extraction`  | `documents/api`                                                               |
| `jobs::integration`      | `jobs/integration`                                                            |
| `position::catalog`      | `position/api/port`                                                           |
| `resume::integration`    | `resume/api/port` + `resume/application/port`                                 |
| `interview::integration` | `interview/api/port` + `interview/application/port` + `interview/integration` |

`identity` 的账户仓储与 OAuth 绑定仓储位于 `identity/application/port`，只由本模块用例与 `/me` 认证读模型消费，不挂具名接口。`activity` 与 `context` 不设具名接口，按需使用根包 Port。

同一 Named Interface 内按职责拆出窄 Port：

| Port                     | 职责                                                    |
| ------------------------ | ------------------------------------------------------- |
| `ModelExecutionPort`     | 冻结模型快照、按快照执行补全与流式调用                  |
| `ModelConfigurationPort` | 读取与修改账户级可变模型配置                            |
| `ModelDiscoveryPort`     | 发现 provider 模型与能力，不修改配置                    |
| `JobDispatcher`          | 请求与取消耐久任务                                      |
| `JobWorker`              | 领取任务、维持租约、报告受 attempt fence 约束的执行结果 |
| `JobQuery`               | 读取账户拥有的任务状态                                  |

`ModelExecutionAdapter` 在同一模块内组合三个 LLM Port 的实现；跨模块消费者只依赖职责对应的窄 Port。背景任务共享命令与结果定义位于 `BackgroundJobMessages`。

## Runtime

| 模块/基础设施 | 当前契约                                                                                                                                                                                                                                                                                                                                                                                               |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `identity`    | 拥有 `user_account` 与 `oauth_binding`；支持 Argon2id 密码与 Google/GitHub OAuth；Spring Session Redis 负责 rotation、logout revoke 与 session revoke；Profile 使用 revision/expectedRevision/operationId 并发契约；`CurrentAccount` 公开认证主体；认证 Session 无 MySQL 表；`OriginPolicy` 统一 HTTP CORS、危险请求 Origin 与 WebSocket handshake；生产强制 Secure Cookie；限流只作用于密码登录与注册 |
| `llm`         | 拥有 DeepSeek 与三种自定义协议、模型路由和 BYOK；调用方显式传入 account id；可变 Configuration 只影响新快照，执行按 `ModelExecutionSnapshot` 读取；不承载会话广播，逐帧广播由 `interview` 经 `activity.RealtimePort` 发出，因而不依赖 `activity`                                                                                                                                                       |
| `assets`      | 二进制真源是 `ObjectStoragePort`，local/CI 使用 VersityGW，`S3ObjectStorageAdapter` 是唯一实现；`AssetRepository` 统一模块内 Asset 持久化；上传按 `PENDING_UPLOAD → READY`；stale PENDING 由模块内有界 reconciler 清理；删除先同事务登记耐久意图，再由有界 worker 租约重试并幂等删除对象与元数据；下载先授权，再发短 TTL 预签名 URL                                                                    |
| `documents`   | 按魔数与 canonical MIME 识别 PDF、DOCX、UTF-8 文本与图片；PDF/DOCX 在限定堆内存的独立 JVM 解析；实施硬超时、页数/像素上限与 DOCX 解压上限                                                                                                                                                                                                                                                              |
| `resume`      | 拥有 PDF 导入、技能/项目解析、资源列表与面试上下文投影；不提供可编辑或版本化工作区                                                                                                                                                                                                                                                                                                                     |
| `position`    | 拥有内置岗位与用户自定义岗位                                                                                                                                                                                                                                                                                                                                                                           |
| `interview`   | 拥有置顶、消息序号、显式阶段、回答评估与确定性状态转换；`pinned_at` 落在 `interview_session`，排序与过滤在内存完成；消息与阶段细节见下表                                                                                                                                                                                                                                                               |
| `artifact`    | 拥有训练报告与 Analytics，不回写简历；正式成果模型见下文；报告异步任务归 `jobs`                                                                                                                                                                                                                                                                                                                        |
| `jobs`        | 调度器回收租约过期的 RUNNING 任务并补投 Modulith 未完成事件；根包 `SchedulingConfig` 开启 `@EnableScheduling`；`prelude.jobs.scheduling-enabled` 可整体关闭；租约回收以 CAS 领取且有批量上限；事件补投有批量与最大在途上限；当前依赖单实例调度，不宣称跨实例去重                                                                                                                                       |
| Redis         | 认证 Session 与实时广播                                                                                                                                                                                                                                                                                                                                                                                |
| RabbitMQ      | 报告任务派发；业务失败留在 `background_job`，无法处理的消息进入 DLQ                                                                                                                                                                                                                                                                                                                                    |
| MySQL         | 业务数据、检索 chunk 与事件发布记录                                                                                                                                                                                                                                                                                                                                                                    |

### Interview 状态真源

| 状态面       | 值/转换                                                                | 真源与约束                                                                                                                                           |
| ------------ | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| 会话生命周期 | `ongoing → generating → finished`；失败或取消从 `generating → ongoing` | `interview_session.status`；`InterviewSessionStatus` 显式拒绝其他转换；只有 `ongoing` 可追加消息和推进阶段；收尾请求以行锁和 CAS 领取唯一转换        |
| 阶段定义     | `warmup → technical → deep_dive → closing`                             | `InterviewStageDefinition` 是唯一阶段目录，携带 wire key、顺序与助手回答配额 1/2/2/1；`InterviewStagePolicy` 与 `InterviewStageCatalog` 从该目录派生 |
| 阶段记录     | `boundary_seq` + `awaiting_answer` + `started_at` + `ended_at`         | `interview_stage`；`ended_at IS NULL` 是当前阶段；边界序号显式标记阶段起点，不再从时间戳猜测；不允许回退或跳阶段；同阶段请求幂等                     |
| 待答状态     | 助手消息后为 `true`，用户消息后为 `false`                              | 与消息追加同事务更新；存在待答提问时拒绝手动推进                                                                                                     |
| 自动推进     | 当前阶段助手回答达到配额                                               | 只统计 `seq_num > boundary_seq` 的助手消息；达到配额后关闭当前阶段并插入下一阶段 system prompt                                                       |
| 消息顺序     | `interview_message.seq_num` 单调递增                                   | `uk_message_session_seq` 唯一键；`InterviewMessageService` 在会话行 `SELECT … FOR UPDATE` 下分配序号；跨进程追加串行，唯一键拒绝绕过分配器的写入     |
| 回合提交     | 助手消息、待答状态与阶段推进同一事务                                   | `InterviewTurnCompletion.complete`；阶段失败回滚助手消息和状态                                                                                       |
| 回答评估     | `PENDING / SCORED / UNSCORED`                                          | `interview_message.assessment_status`；用户回答初始为 PENDING；模型失败落 UNSCORED 并保存错误；重试调度只处理超龄 PENDING                            |
| 生成报告     | 仅 `closing` 且无 PENDING 评估                                         | 同事务关闭阶段、将会话置为 GENERATING 并请求耐久 Job；重复请求复用当前状态，不重复创建执行                                                           |
| 报告完成     | Job CAS、报告与 Analytics 同事务                                       | worker 先验证 attempt fence；`GENERATING → FINISHED` 必须仍由当前 worker 领取；实时 `report_ready` 只是通知，查询快照是真源                          |

### 异步与提交边界

| 机制         | 当前行为                                                                                                                                                             |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 开面检索     | `StartInterview` 在事务内冻结简历、JD 与附件文本，并通过 `RetrievalTransactions.afterCommit` 注册提交后动作，再由 `sseTaskExecutor` 异步 index；回滚不会留下派生索引 |
| 删除后检索   | 会话与附件元数据删除后，`RetrievalTransactions.afterCommit` 才 invalidate；失效失败时该 scope 在重试成功前对搜索不可见，并由调度器有界重试                           |
| 检索恢复     | 内存索引缺失时先读持久 chunk；持久 chunk 也缺失时，由 `RetrievalSourcePort` 从已提交 MySQL 的简历、JD 与附件元数据重建                                               |
| 事件外发     | `BackgroundJobRequested` 在业务事务内发布，Spring Modulith 将 publication 与业务数据一起持久化，提交后外发到 RabbitMQ                                                |
| 发布恢复     | 未完成 publication 由有界补投重试；恢复走同一事件路径，不直接调用 `RabbitTemplate`                                                                                   |
| 生命周期通知 | `ReportJobLifecycle` 在耐久终态提交后收敛会话并发送实时通知；实时投递失败不改变耐久结果                                                                              |
| Asset 删除   | 业务引用删除与 `asset_deletion_request` 登记同事务；worker 先删对象，再以当前 attempt fence 删除 Asset 元数据与请求；存储失败保留元数据并指数退避                    |
| 上传发布     | 对象先写入 S3；附件业务行与 `PENDING_UPLOAD → READY` 在同一事务提交；失败回滚后由 stale reconciler 清理                                                              |

## Persistence

| 规则             | 契约                                                                                                             |
| ---------------- | ---------------------------------------------------------------------------------------------------------------- |
| 关系数据库       | MySQL 是唯一关系数据库；资源所有权统一为 `account_id`                                                            |
| DDL owner        | Flyway 是唯一 DDL 执行器；migration 位于 `backend/src/main/resources/db/migration/`                              |
| 版本序列         | 单一全局版本序列；baseline 为 `V20260830__establish_prelude_schema.sql`，其后使用 `V<日期>__<语义>.sql` 增量文件 |
| 已应用版本       | 版本文件一旦应用即保持字节不变；索引、约束与列变更走新增版本文件                                                 |
| Reference data   | 幂等 `R__reference_data.sql` 维护                                                                                |
| 二进制与 Session | `attachment` 只保存业务元数据并以 `asset_id` 引用二进制；认证 Session 与二进制内容不在 MySQL                     |
| Modulith 事件表  | `EVENT_PUBLICATION` 由同一 baseline 建立；自动建表关闭                                                           |

## 路线图占位模块

四个模块只保留 `package-info.java` 拓扑占位，由 `ApplicationModulesTest` 锁定为 16 模块契约。它们是开放 Issue 对应的显式路线图契约，不是死代码；Issue 关闭、被替代或正式开工时重新评估，未满足条件前不填充、不删除。

| 模块 / Issue                                                        | 保留职责                                                        | 当前运行时落点                                        | 重新评估条件                                                                                            |
| ------------------------------------------------------------------- | --------------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `agent` / [#37](https://github.com/zyyyyynnn/Prelude/issues/37)     | LangGraph4j 面试执行、durable checkpoint、HITL 审批与可恢复 Run | `interview` / `llm` 直接完成编排                      | Issue 批准图运行时、Run/Step/Approval 持久化与 MySQL 真源方案，并明确 checkpoint 一致性、幂等与恢复验收 |
| `tools` / [#42](https://github.com/zyyyyynnn/Prelude/issues/42)     | ToolDefinition、权限、风险与 MCP 配置                           | Tool binding 经 `ModelExecutionPort.ToolBinding` 传入 | Issue 批准 Tool 元数据、`deny > ask > allow` 权限、MCP egress 与副作用幂等边界                          |
| `settings` / [#45](https://github.com/zyyyyynnn/Prelude/issues/45)  | Git-backed SettingDefinition 与 `setting_override`              | Profile 在 `identity`，模型 Configuration 在 `llm`    | Issue 批准 typed definitions、override 并发与 Settings/Composer 分工，且不复制 Profile 或模型表         |
| `telemetry` / [#46](https://github.com/zyyyyynnn/Prelude/issues/46) | Trace、不可变 Usage ledger、Eval 与 Release Gates               | `llm` 发布 `LlmUsageRecorded`，无消费模块             | Issue 批准 Micrometer/OpenTelemetry、usage 幂等、成本未知语义与可选 Langfuse 边界                       |

LangGraph4j 与 MCP SDK 当前无生产引用；依赖保留服务于上述开放契约。`FrameworkLeakageTest` 禁止它们与 Spring stereotype、Jackson、持久化框架进入 `domain`，为落地时保留隔离边界。拓扑调整必须同步模块占位、`allowedDependencies`、本表与验证器。

## Artifact 正式成果发布模型

| 项         | 决策                                                                                                                                                 |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| 当前消费者 | 无生产消费者；报告当前直接存入 `interview_session.summary_report` 并供 Analytics 使用                                                                |
| 保留契约   | [#36](https://github.com/zyyyyynnn/Prelude/issues/36) 的公共契约、`artifact` / `artifact_version` 表、领域类型与测试继续保留                         |
| 发布入口   | 只有 `ArtifactCommandApi.publishVersion`；其他模块不得直接写表                                                                                       |
| 版本语义   | 按 `(accountId, kind)` 定位 Artifact；版本号由数据库约束分配，并发冲突有限重试；已发布版本不可变，新内容创建下一版本                                 |
| 内容引用   | 版本通过可选 `assetId` 引用本账户 READY Asset；发布前校验所有权                                                                                      |
| Provenance | 使用业务 ID/revision，不使用 SHA 充当 Artifact provenance                                                                                            |
| 接入条件   | 出现 Interview Report、Resume Export 等真实消费者时，经 Named Interface/API 接入，并补齐跨模块账户隔离、不可变版本、Asset 生命周期与 provenance 验收 |
| 移除条件   | 明确由后续架构决策替代 #36，且无消费者、无已发布数据；在带数据迁移与保留审批的前提下，同一次变更移除表、API、类型与旧契约测试                        |

## 命名规约

| 范围     | 规约                                                                                              |
| -------- | ------------------------------------------------------------------------------------------------- |
| 领域主体 | 统一 `Account`；对外兼容称谓保留 `User`                                                           |
| 模型档案 | 领域称 `Profile`；对外读写称 `Configuration`                                                      |
| 模型     | 读模型用 `Response`/`View`，写模型用 `Request`/`Command`                                          |
| 测试夹具 | Mockito 用 `verify*`，AssertJ 用 `assert*`，JDBC 直写用 `select*`/`update*`，Captor 用 `capture*` |
| 冻结语义 | 条件查询结果用 `frozen*` 前缀                                                                     |

## 验证器

| 验证器                                                                 | 断言                                                                                                                                                                                                                                   |
| ---------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ApplicationModulesTest`                                               | 恰好 16 个模块；`ApplicationModules.verify()` 通过                                                                                                                                                                                     |
| `FrameworkLeakageTest`                                                 | `domain` 不依赖 Spring AI、LangGraph4j、MCP SDK、AWS SDK、Spring stereotype、Jackson、MyBatis-Plus 或 Apache IBatis；`api` 不依赖持久化与 Web；`application` 不依赖持久化、multipart、HTTP Session 或 `SseEmitter`；根包不依赖业务模块 |
| `InterviewSessionStatusTest`                                           | 会话生命周期只接受显式合法转换                                                                                                                                                                                                         |
| `InterviewStageCatalogTest`                                            | 阶段目录、wire key、顺序与配额单一来源                                                                                                                                                                                                 |
| `InterviewStagePolicyTest`                                             | 阶段归一化、顺序、不可回退与配额                                                                                                                                                                                                       |
| `InterviewTurnCompletionTest`                                          | 助手消息、待答状态与阶段推进同事务；阶段失败回滚                                                                                                                                                                                       |
| `InterviewMessageStatusGateTest`                                       | 仅 `ongoing` 允许追加消息                                                                                                                                                                                                              |
| `InterviewMessageOrderMySqlTest`                                       | 行锁、`seq_num` 与唯一键在 MySQL 并发追加下保持顺序                                                                                                                                                                                    |
| `FinishInterviewStatusRaceTest`                                        | 并发收尾只领取一次 `ongoing → generating`                                                                                                                                                                                              |
| `InterviewJudgeMySqlTest`                                              | 评估状态、错误持久化与重试幂等                                                                                                                                                                                                         |
| `AssetDeletionWorkerTest`                                              | 删除顺序、重试、所有权/key 不一致阻断                                                                                                                                                                                                  |
| `AssetDeletionMySqlTest`                                               | 意图与业务删除同事务、并发登记、租约回收与存储失败恢复                                                                                                                                                                                 |
| `BackgroundJobAmqpContractTest`                                        | 提交后事件解码、Job 执行与 DLQ                                                                                                                                                                                                         |
| `BackgroundJobPublicationRecoveryTest`                                 | 外发失败后 publication 保持耐久并可重投                                                                                                                                                                                                |
| `SchedulingEnabledTest`                                                | 钉住 jobs 调度开关语义                                                                                                                                                                                                                 |
| `BackgroundJobMaintenanceSchedulingTest`                               | 租约回收与 publication 补投方法已登记                                                                                                                                                                                                  |
| `StartInterviewTest` / `DeleteInterviewSessionTest`                    | 检索 index/invalidate 只在提交后执行；回滚不留下索引或触发失效                                                                                                                                                                         |
| `InterviewRetrievalSourceAdapterTest` / `InMemoryRetrievalAdapterTest` | 从已提交会话源重建统一文档、索引恢复与 embedding 回退                                                                                                                                                                                  |
| `ArtifactPublishServiceTest`                                           | 不可变递增版本与 Asset 账户校验                                                                                                                                                                                                        |

## 依赖台账

| 依赖                          | 许可证                      | 用途与边界                                                   |
| ----------------------------- | --------------------------- | ------------------------------------------------------------ |
| Spring Boot                   | Apache-2.0                  | Runtime 与依赖管理                                           |
| Spring Modulith               | Apache-2.0                  | Application Module 发现与验证                                |
| Flyway                        | Apache-2.0                  | 唯一 DDL 执行器                                              |
| Spring Security OAuth2 Client | Apache-2.0                  | Google/GitHub OAuth 登录                                     |
| Spring Session Data Redis     | Apache-2.0                  | 认证会话存储                                                 |
| Bouncy Castle                 | MIT                         | Argon2id 密码哈希                                            |
| AWS SDK for Java v2           | Apache-2.0                  | `assets` 模块内 S3 兼容对象存储                              |
| MyBatis-Plus                  | Apache-2.0                  | 模块内持久化 adapter                                         |
| Spring AI                     | Apache-2.0                  | `llm` 模块内的模型框架边界                                   |
| LangGraph4j                   | Apache-2.0                  | 已声明未使用；服务 `agent` 开放路线图契约，`src/main` 无引用 |
| MySQL Connector/J             | GPL-2.0 with FOSS exception | MySQL runtime 驱动                                           |

依赖清单与解析结果以 `backend/pom.xml` 为唯一真源。
