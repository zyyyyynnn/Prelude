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

- `identity` 拥有账号、服务端 Session 登录与用户资料。
- `llm` 拥有 DeepSeek 与三种自定义协议、模型路由和 BYOK 配置。
- `assets` 拥有面试附件的存储、归属校验与上下文投影，`documents` 负责受支持文档的内容提取。
- `resume` 拥有简历导入、结构化文档与面试上下文投影。
- `template` 拥有内置岗位与用户自定义岗位。
- `interview` 拥有会话、阶段与文字面试用例，`voice` 拥有语音通道。
- `artifact` 拥有训练报告与分析，报告不会回写简历；`jobs` 拥有报告异步任务。
- Redis 承载实时广播能力，RabbitMQ 承载报告任务，MySQL 承载业务数据和 Spring Session。

## Persistence

- MySQL 是唯一关系数据库。
- Flyway 是唯一 DDL owner。
- 所有 migration 位于 `backend/src/main/resources/db/migration/`。
- 已应用 migration 保持不可变，后续变更使用 forward-only migration。

## 依赖台账

| 依赖 | 许可证 | 用途与边界 |
| --- | --- | --- |
| Spring Boot | Apache-2.0 | Runtime 与依赖管理 |
| Spring Modulith | Apache-2.0 | Application Module 发现与验证 |
| Flyway | Apache-2.0 | 唯一 DDL 执行器 |
| MyBatis-Plus | Apache-2.0 | 模块内持久化 adapter |
| Spring AI | Apache-2.0 | `llm` 模块内的模型框架边界 |
| LangGraph4j | Apache-2.0 | `agent` 模块内的图编排基础 |
| MySQL Connector/J | GPL-2.0 with FOSS exception | MySQL runtime 驱动 |

依赖清单与解析结果以 `backend/pom.xml` 为唯一真源。
