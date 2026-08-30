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

模块根包是默认公共接口。额外公共包使用 `@NamedInterface`，内部层级按真实类型职责建立。跨模块依赖写入 `@ApplicationModule.allowedDependencies`，并形成有向无环图。

Port 用于外部基础设施、框架隔离或跨模块接口。普通内部类直接表达其职责。`domain` 保持框架无关，专项 ArchUnit 测试验证 Spring AI、LangGraph4j、MCP SDK 与 AWS SDK 的隔离。

## Runtime

- `identity` 拥有 `user_account` 与 `oauth_binding`：密码（Argon2id）与 Google/GitHub OAuth 绑定登录、Spring Session Redis 会话（rotation、logout revoke、session revoke）、profile revision/expectedRevision/operationId 并发契约，并通过 `CurrentAccount` 公开认证主体。认证 Session 无 MySQL 表。
- `llm` 拥有 DeepSeek 与三种自定义协议、模型路由和 BYOK 配置；account id 由调用方显式传入，会话级广播关联由模块内 `LlmInvocationContext` 承载。
- `assets` 拥有 `asset` 与面试附件：二进制真源是 `ObjectStoragePort`（S3 兼容，local/CI = VersityGW），`S3ObjectStorageAdapter` 是唯一实现；上传按 PENDING_UPLOAD → READY 流转，stale PENDING 由模块内 bounded reconciler 清理；下载先授权后短 TTL 预签名。`documents` 负责受支持文档的内容提取。
- `resume` 拥有 PDF 简历导入、技能与项目解析、资源列表和面试上下文投影；当前不提供可编辑或版本化的简历工作区。
- `template` 拥有内置岗位与用户自定义岗位。
- `interview` 拥有会话、阶段与文字面试用例，`voice` 拥有语音通道。
- `artifact` 拥有训练报告与分析（不回写简历）、`artifact`/`artifact_version` 正式成果基础模型（版本 immutable，发布走公开 API），以及 Analytics 视图；`jobs` 拥有报告异步任务。
- Redis 承载认证会话与实时广播，RabbitMQ 承载报告任务，MySQL 承载业务数据。

## Persistence

- MySQL 是唯一关系数据库；所有资源所有权统一为 `account_id`。
- Flyway 是唯一 DDL owner，所有 migration 位于 `backend/src/main/resources/db/migration/`，使用单一全局版本序列（当前 baseline：`V20260830__establish_prelude_schema.sql`），reference data 由幂等的 `R__reference_data.sql` 维护。
- `attachment` 只保存业务元数据并以 `asset_id` 引用二进制；认证 Session、二进制内容均不在 MySQL。

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
