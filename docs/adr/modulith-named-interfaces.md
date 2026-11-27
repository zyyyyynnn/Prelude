# Modulith 与 Named Interface

## Context

仓库在一个可部署后端中保留 16 个 Application Modules。模块根包默认可见，若依赖包名约定而非显式授权，业务边界会逐步退化；后台执行、模型配置与面试集成又需要不同粒度的消费者接口。

## Decision

- `PreludeApplication` 是唯一 Composition Root；`ApplicationModulesTest` 锁定 16 个模块并执行 `ApplicationModules.verify()`。
- 跨模块面使用九个 `@NamedInterface`；`application`、`domain`、`infrastructure`、`web` 不对外可见。
- `activity.RealtimePort` 与 `context.RetrievalPort` 是根包可见面的显式例外，按裸模块名授权。
- `interview` 的仓储与领域不导出；跨模块只消费集成 Port 与快照投影。
- `llm.api` 拆出 `ModelExecutionPort`、`ModelConfigurationPort`、`ModelDiscoveryPort`；`jobs.integration` 拆出 `JobDispatcher`、`JobWorker`、`JobQuery`。聚合接口保留共享消息，但消费者按职责依赖窄 Port。
- 四个路线图占位模块继续参加拓扑验证，不因暂无生产类型而删除。

## Consequences

模块新增依赖必须更新 `allowedDependencies` 与具名接口，不能靠根包或内部包穿透。窄 Port 减少消费者对无关操作的可见性，也要求接口拆分与消息类型保持单一归属。占位模块会暂时增加拓扑数量，但换来稳定边界与明确的重新评估条件。

## Validation

`ApplicationModulesTest` 验证模块集合、环与内部访问；`FrameworkLeakageTest` 验证 domain/API/application/root 的框架、持久化、Web 与模块依赖隔离。命令：`mvn -f backend/pom.xml clean test`。
