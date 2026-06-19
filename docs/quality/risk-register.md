# Residual Risk Register

本台账只保留当前仍需跟踪的工程风险，以及已关闭风险的索引。详细依赖解释不在 active 文档中长期展开，避免旧风险持续制造噪音。

## 当前观察项

### R-003 — TTS 池跨 session 串行吞吐 — 保留观察

| 字段 | 内容 |
| --- | --- |
| Recorded in | `588bf73` |
| Current status | P3 观察项 |
| Location | `backend/src/main/java/com/interview/config/ThreadPoolConfig.java#ttsTaskExecutor`、`VoiceInterviewTurnService` |
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
