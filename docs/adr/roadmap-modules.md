# 路线图占位模块

## Context

`agent`、`tools`、`settings`、`telemetry` 对应仍开放的 Issue，但当前运行时由既有模块承担相关职责。把占位模块当死代码删除会破坏 16 模块拓扑；直接填充又会绕过 Issue 的所有权与验收契约。

## Decision

- 保留四个 `package-info.java` 占位与 LangGraph4j/MCP 依赖，作为显式路线图契约。
- `agent` 对应 [#37](https://github.com/zyyyyynnn/Prelude/issues/37)：LangGraph4j、durable checkpoint、HITL 与可恢复 Run。
- `tools` 对应 [#42](https://github.com/zyyyyynnn/Prelude/issues/42)：ToolDefinition、权限、风险与 MCP。
- `settings` 对应 [#45](https://github.com/zyyyyynnn/Prelude/issues/45)：Git-backed SettingDefinition 与 `setting_override`；Profile 仍归 `identity`，模型配置仍归 `llm`。
- `telemetry` 对应 [#46](https://github.com/zyyyyynnn/Prelude/issues/46)：Trace、不可变 Usage、Eval 与可选 Langfuse。
- Issue 关闭、被明确替代或正式开工时重新评估；填充或删除都必须同步 `allowedDependencies`、拓扑测试与 Runtime 文档。

## Consequences

占位模块不是当前生产能力，不得被根包或业务模块提前消费。它们表达未来所有权，不授权提前建表或复制 Profile/模型数据。重新评估前，运行时继续使用 `interview`/`llm` 编排、`ModelExecutionPort.ToolBinding` 与 `LlmUsageRecorded`。

## Validation

`ApplicationModulesTest` 钉住 16 模块集合与依赖；`FrameworkLeakageTest` 禁止 LangGraph4j/MCP 进入 domain，并禁止根包消费四个占位模块。Issue 状态变化时，以对应 Issue 的 Acceptance 作为落地或移除的验证清单。
