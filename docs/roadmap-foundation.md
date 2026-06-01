# Prelude Roadmap Foundation

本文件记录已为 `prelude-roadmap.md` 预留的最小骨架，后续实现应优先复用这些入口，避免重复扩展契约。

## Phase 1

- `LlmRouter.chatWithSnapshot(...)` 已保留旧签名，并新增 `extraParams` overload。
- Structured Output 应通过 `extraParams` 注入 `response_format`，不要绕过 `LlmRouter` 直接调用 Provider。
- `/finish` 已接入 `InterviewReportParser`，当前结构化报告契约如下：

```json
{
  "reportMarkdown": "完整 Markdown 评估报告",
  "scores": {
    "technical": 1,
    "expression": 1,
    "logic": 1
  }
}
```

- 三个评分固定为 1-10 整数；解析失败时报告保留原文，评分安全降级为 6。
- `streamInterviewChat(...)` 已提供 `onEvent` 分发入口。后续 SSE 重连、`judge`、`report_ready` 事件应先接入该统一事件通道。

## Phase 2

- `interview_session.summary` 已在 `schema.sql` 和 `InterviewSession` 实体中占位。
- Sliding Window Memory 后续应写入该字段，并在构造 LLM 上下文时组合 `summary + 最近消息`。
- Redis、WebSocket、Judge 目前未引入依赖；实现时应作为独立阶段提交，避免影响当前 Demo 基线。

## Phase 3

- PDF 导出和语音交互未引入依赖。
- PDF 导出可从 `InterviewView.vue` 的报告渲染区域接入；语音事件仍应复用统一 SSE/WebSocket 状态模型。

## Phase 4

- 异步报告完成通知应使用 `streamInterviewChat` 的 `onEvent` 处理 `report_ready`。
- 限流、熔断、Redis MQ、监控和容器化均未落地；先补基础设施，再改业务流程。

## Verification Baseline

每轮实现后至少执行：

```powershell
cd .\backend
mvn -q test

cd ..\frontend
npm run build
```
