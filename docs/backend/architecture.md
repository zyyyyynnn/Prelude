# 后端架构

## Composition Root

`com.prelude.PreludeApplication` 是唯一 Composition Root。Spring Modulith 从该根包发现 Application Modules，并由 `ApplicationModules.verify()` 验证环、内部访问和显式依赖。

## Application Modules

```text
identity  settings  llm       tools
context   agent     artifact  assets
jobs      resume    position  documents
interview voice     activity  telemetry
```

模块之间通过 `@NamedInterface` 声明的具名接口协作；具名接口之外的子包（`application`、`domain`、`infrastructure`、`web`）不对外可见。模块**根包**是 Spring Modulith 的默认可见面，因此 `activity` 的 `RealtimePort`、`context` 的 `RetrievalPort` 直接落在根包、由 `artifact` 与 `interview` 以裸模块名授权引用；其余模块的跨模块面一律走具名接口，不靠根包暴露。八个具名接口及其声明包：`identity::api`（`identity/api`）、`llm::api`（`llm/api`）、`assets::integration`（`assets/api`）、`documents::extraction`（`documents/api`）、`jobs::integration`（`jobs/integration`）、`position::catalog`（`position/api/port`）、`resume::integration`（`resume/api/port` + `resume/application/port`）、`interview::integration`（`interview/api/port` + `interview/application/port`）。`identity` 的账户仓储与 OAuth 绑定仓储留在 `identity/application/port`：它们只被 `identity` 自己的用例消费，不再挂具名接口对外。

`interview/application/repository` 不导出：模块内 MyBatis 仓储端口留在模块内部，跨模块只经 `interview/application/port/InterviewSessionGuard` 取会话摘要。`interview/domain` 同样不导出：跨模块只经上述两个 port 包的快照投影读取与驱动面试。`activity` 与 `context` 不设具名接口，按需直接引用。HTTP 适配器按模块收在 `web` 包；`HealthController` 与 `GlobalExceptionHandler` 属全局横切，放在根包，其余控制器进模块 `web` 包。

Port 用于外部基础设施、框架隔离或跨模块接口；普通内部类直接表达其职责。`domain` 保持框架无关：`FrameworkLeakageTest` 禁止 Spring AI、LangGraph4j、MCP SDK 与 AWS SDK 进入 `..domain..`。其中 LangGraph4j 与 MCP SDK 当前无任何生产引用，该禁令是为 `agent` / `tools` 落地预留的边界，不是在描述现状；`domain` 仍带着 Spring 的 `@Component` 与 Jackson 注解各一处，这两类尚未纳入禁包。

### 规划中模块

下列模块只保留 `package-info.java` 拓扑占位，由 `ApplicationModulesTest` 锁定为 16 模块契约的一部分。对应能力落地时填充真实类型、更新 `allowedDependencies`，并把本表状态改为「已落地」、同步 Runtime 一节。

| 模块 | 规划职责 | 现状 |
| --- | --- | --- |
| `agent` | LangGraph4j 图编排（面试流程编排基础设施） | 仅 `@ApplicationModule` 声明；运行时编排由 `interview` / `llm` 直接完成 |
| `settings` | 账号级设置聚合边界 | 仅占位；用户资料与模型配置落在 `identity` / `llm` |
| `telemetry` | 使用量与运行时遥测归属 | 仅占位；`LlmUsageRecorded` 事件由 `llm` 发布 |
| `tools` | 面试工具调用（tool-calling）归属 | 仅占位；工具绑定经 `LlmPort.ToolBinding` 传入 |

## Runtime

- `identity` 拥有 `user_account` 与 `oauth_binding`：密码（Argon2id）与 Google/GitHub OAuth 绑定登录、Spring Session Redis 会话（rotation、logout revoke、session revoke）、profile revision/expectedRevision/operationId 并发契约，并通过 `CurrentAccount` 公开认证主体。认证 Session 无 MySQL 表。
- `llm` 拥有 DeepSeek 与三种自定义协议、模型路由和 BYOK 配置；account id 由调用方显式传入。跨模块契约（`LlmPort`、`EmbedPort`、能力/配置视图）位于 `llm.api`。`llm` 不承载会话广播：模型输出的逐帧广播由 `interview` 经 `activity` 的 `RealtimePort` 发出，`llm` 不依赖 `activity`。
- `assets` 拥有 `asset` 与面试附件：二进制真源是 `ObjectStoragePort`（S3 兼容，local/CI = VersityGW），`S3ObjectStorageAdapter` 是唯一实现；上传按 PENDING_UPLOAD → READY 流转，stale PENDING 由模块内 bounded reconciler 清理；下载先授权后短 TTL 预签名。`documents` 负责受支持文档的内容提取。
- `resume` 拥有 PDF 简历导入、技能与项目解析、资源列表、面试上下文投影，以及简历工作会话（`resume_conversation` / `resume_turn` / `resume_assistant_message` / tool-call 组）：一轮用户指令对应若干助手消息，每条消息可挂一组 toolcall；组状态只有 running/done，失败落在单条工具行。
- `position` 拥有内置岗位与用户自定义岗位。
- `interview` 拥有会话置顶与消息序号。置顶字段落在 `interview_session.pinned_at`，排序与过滤在内存完成。消息序号由 `interview_message.seq_num`、唯一键 `uk_message_session_seq` 与 `interview_session` 行锁共同保证：`InterviewMessageService.insertMessage` 在会话行 `SELECT … FOR UPDATE` 下分配序号，同一会话的追加跨进程串行；唯一键拒绝任何绕过分配逻辑的写入。`voice` 的回合编排只经 `interview` 的集成端口驱动会话。
- `artifact` 拥有训练报告与分析（不回写简历）、`artifact`/`artifact_version` 正式成果基础模型（版本 immutable，发布走公开 API），以及 Analytics 视图；`jobs` 拥有报告异步任务。
- `jobs` 的两条自愈通道由调度器周期执行：租约过期的 RUNNING 任务回收，以及 Modulith 未完成事件的补投。调度由根包 `SchedulingConfig` 的 `@EnableScheduling` 打开，`prelude.jobs.scheduling-enabled` 可整体关闭；两条通道都以 CAS 领取工作，重复执行无副作用。`SchedulingEnabledTest` 钉住开关，`BackgroundJobMaintenanceSchedulingTest` 钉住两个方法确实被登记。
- Redis 承载认证会话与实时广播，RabbitMQ 承载报告任务，MySQL 承载业务数据。

## Persistence

- MySQL 是唯一关系数据库；所有资源所有权统一为 `account_id`。
- Flyway 是唯一 DDL owner，migration 位于 `backend/src/main/resources/db/migration/`，使用单一全局版本序列（baseline `V20260830__establish_prelude_schema.sql`，其后 `V20260923__make_message_order_authoritative.sql` 等增量文件），reference data 由幂等的 `R__reference_data.sql` 维护。版本文件一旦应用即保持字节不变；索引、约束与列的变更走新增版本文件。
- `attachment` 只保存业务元数据并以 `asset_id` 引用二进制；认证 Session、二进制内容均不在 MySQL。Spring Modulith 事件发布表 `EVENT_PUBLICATION` 由同一 baseline 建立，自动建表保持关闭。

## 命名规约

领域主体统一 `Account`，对外兼容称谓保留 `User`；模型档案领域称 `Profile`，对外读写称 `Configuration`。读模型用 `Response`/`View`、写模型用 `Request`/`Command`。测试夹具按分层选用动词：Mockito 校验 `verify*`、AssertJ 断言 `assert*`、JDBC 直写 `select*`/`update*`、Captor 抽取 `capture*`；条件查询结果用 `frozen*` 前缀表达冻结语义。

## 依赖台账

| 依赖 | 许可证 | 用途与边界 |
| --- | --- | --- |
| Spring Boot | Apache-2.0 | Runtime 与依赖管理 |
| Spring Modulith | Apache-2.0 | Application Module 发现与验证 |
| Flyway | Apache-2.0 | 唯一 DDL 执行器 |
| Spring Security OAuth2 Client | Apache-2.0 | Google/GitHub OAuth 登录 |
| Spring Session Data Redis | Apache-2.0 | 认证会话存储 |
| Bouncy Castle | MIT | Argon2id 密码哈希 |
| AWS SDK for Java v2 | Apache-2.0 | `assets` 模块内 S3 兼容对象存储 |
| MyBatis-Plus | Apache-2.0 | 模块内持久化 adapter |
| Spring AI | Apache-2.0 | `llm` 模块内的模型框架边界 |
| LangGraph4j | Apache-2.0 | 已声明未使用：为 `agent` 的图编排预留，`src/main` 无引用 |
| MySQL Connector/J | GPL-2.0 with FOSS exception | MySQL runtime 驱动 |

依赖清单与解析结果以 `backend/pom.xml` 为唯一真源。
