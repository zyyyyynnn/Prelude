# 后端架构

## Composition Root

`com.prelude.PreludeApplication` 是唯一 Composition Root。Spring Modulith 从该根包发现 Application Modules，并由 `ApplicationModules.verify()` 验证环、内部访问和显式依赖。

## Application Modules

```text
identity  settings  llm       tools
context   agent     artifact  assets
jobs      resume    template  documents
interview voice     activity  telemetry
```

模块根包是默认公共接口。额外公共包使用 `@NamedInterface`，内部层级按真实类型职责建立。跨模块契约只通过 NamedInterface 暴露：`llm.api`、`identity.api`、`assets.api`、`interview.application.port` / `interview.api.port` / `interview.domain`、`resume.*.port`、`template.api.port`、`jobs.integration`、`documents.api`。HTTP 适配器一律放在各模块 `web` 包，不得出现在 `api` NamedInterface 中。

Port 用于外部基础设施、框架隔离或跨模块接口。普通内部类直接表达其职责。`domain` 保持框架无关，专项 ArchUnit 测试验证 Spring AI、LangGraph4j、MCP SDK 与 AWS SDK 的隔离。

### 规划中模块（当前无实现）

下列模块仅保留 `package-info.java` 拓扑占位，被 `ApplicationModulesTest` 锁定为 16 模块契约的一部分，**尚无业务实现**。在对应能力落地前，不要向这些包添加代码，也不要删除占位，以免破坏模块拓扑验证。

| 模块 | 规划职责 | 现状 |
| --- | --- | --- |
| `agent` | LangGraph4j 图编排（面试流程编排基础设施） | 仅 `@ApplicationModule` 声明；运行时编排暂由 `interview` / `llm` 直接完成 |
| `settings` | 账号级设置聚合边界 | 仅占位；用户资料与模型配置当前落在 `identity` / `llm` |
| `telemetry` | 使用量与运行时遥测归属 | 仅占位；`LlmUsageRecorded` 事件由 `llm` 发布，尚无独立消费模块 |
| `tools` | 面试工具调用（tool-calling）归属 | 仅占位；工具绑定目前直接经 `LlmPort.ToolBinding` 传入 |

实现上述任一模块时：填充真实类型 → 更新 `allowedDependencies` → 在本表将其状态改为「已落地」，并同步 Runtime 一节。

## Runtime

- `identity` 拥有 `user_account` 与 `oauth_binding`：密码（Argon2id）与 Google/GitHub OAuth 绑定登录、Spring Session Redis 会话（rotation、logout revoke、session revoke）、profile revision/expectedRevision/operationId 并发契约，并通过 `CurrentAccount` 公开认证主体。认证 Session 无 MySQL 表。
- `llm` 拥有 DeepSeek 与三种自定义协议、模型路由和 BYOK 配置；account id 由调用方显式传入，会话级广播关联由模块内 `LlmInvocationContext` 承载。跨模块契约（`LlmPort`、`EmbedPort`、能力/配置视图）位于 `llm.api`。
- `assets` 拥有 `asset` 与面试附件：二进制真源是 `ObjectStoragePort`（S3 兼容，local/CI = VersityGW），`S3ObjectStorageAdapter` 是唯一实现；上传按 PENDING_UPLOAD → READY 流转，stale PENDING 由模块内 bounded reconciler 清理；下载先授权后短 TTL 预签名。`documents` 负责受支持文档的内容提取。
- `resume` 拥有 PDF 简历导入、技能与项目解析、资源列表和面试上下文投影；当前不提供可编辑或版本化的简历工作区。
- `template` 拥有内置岗位与用户自定义岗位。
- `interview` 拥有会话、阶段与文字面试用例，`voice` 拥有语音通道。
- `artifact` 拥有训练报告与分析（不回写简历）、`artifact`/`artifact_version` 正式成果基础模型（版本 immutable，发布走公开 API），以及 Analytics 视图；`jobs` 拥有报告异步任务。
- Redis 承载认证会话与实时广播，RabbitMQ 承载报告任务，MySQL 承载业务数据。

## Persistence

- MySQL 是唯一关系数据库；所有资源所有权统一为 `account_id`。
- Flyway 是唯一 DDL owner，所有 migration 位于 `backend/src/main/resources/db/migration/`，使用单一全局版本序列（当前 baseline：`V20260830__establish_prelude_schema.sql`），reference data 由幂等的 `R__reference_data.sql` 维护。
- `attachment` 只保存业务元数据并以 `asset_id` 引用二进制；认证 Session、二进制内容均不在 MySQL。Spring Modulith 事件发布表 `EVENT_PUBLICATION` 由同一 baseline 建立，自动建表保持关闭。

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
| LangGraph4j | Apache-2.0 | `agent` 模块内的图编排基础 |
| MySQL Connector/J | GPL-2.0 with FOSS exception | MySQL runtime 驱动 |

依赖清单与解析结果以 `backend/pom.xml` 为唯一真源。
