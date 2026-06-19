# Residual Dependency Risk Register

事实化记录 Prelude 尚未解决的传递依赖漏洞。每条记录都明确触发路径与缓解措施，避免营销话术。

> 上一轮（audit base `31272bc`）已将 `axios` 与 `markdown-it` 升级至安全版本，
> 本台账跟踪无法在当前约束下消除的传递依赖风险，并在条件变化时记录处置结果。
>
> 本台账首次落地于 commit `b39eee1`（docs: add docs/quality/risk-register.md）。
> 后续如审计基线变化，应在每条记录旁追加 `Recorded in | <commit>` 而不要回填旧的审计基线。

---

## 风险 R-001 — `form-data` CRLF injection (high) — **已关闭**

| 字段 | 内容 |
|---|---|
| Audit base | 31272bc（在该 commit 上完成 npm audit 复核） |
| Recorded in | b39eee1（首次登记） |
| Closed in | 588bf73（通过 npm overrides 升级至 4.0.6，audit 0 vulnerabilities） |
| CVE / Advisory | GHSA-hmw2-7cc7-3qxx |
| Severity | high |
| Affected range | form-data `4.0.0` – `4.0.5` |
| Closing path | `frontend/package.json#overrides.form-data = 4.0.6` |

### Dependency chain

```
frontend
└── axios@1.18.0            (直接依赖，本轮已升级)
    └── form-data@4.0.5     (axios 内部依赖，^4.0.5)
```

### Trigger path（历史）

- 漏洞位于 `form-data` 的 multipart 序列化逻辑（`FormData#getBoundary` / 字段名 CRLF 注入）。
- 该路径只由 axios 的 **Node.js adapter** 触发，用于把 `FormData` 序列化为 multipart 字节流。
- axios 浏览器路径默认使用 `xhr` / `fetch` adapter，**不经过 `form-data`**。

### Closing decision

通过 `package.json#overrides` 将 form-data 强制钉到 `4.0.6`（npm 上已发布的 patch 版本，
修复了 CRLF 注入）。npm audit 在本轮变更后返回 `found 0 vulnerabilities`。

### Next review condition

- 若审计出现新的 form-data 高/严重级 CVE，且 overrides 无法覆盖（无 patch 版），恢复台账登记。
- 若 axios 后续 major 升级并自带 form-data >= 4.0.6，可评估移除 overrides。

---

## 风险 R-002 — `dompurify` 多项配置污染 / bypass (moderate) — **已关闭**

| 字段 | 内容 |
|---|---|
| Audit base | 31272bc（在该 commit 上完成 npm audit 复核） |
| Recorded in | b39eee1（首次登记） |
| Closed in | 588bf73（通过 npm overrides 升级至 3.4.11，audit 0 vulnerabilities） |
| Advisories | GHSA-vxr8-fq34-vvx9, GHSA-gvmj-g25r-r7wr, GHSA-cmwh-pvjr-275q, GHSA-cmwh-pvxp-8882 |
| Severity | moderate (聚合) |
| Affected range | dompurify `<= 3.4.10` |
| Closing path | `frontend/package.json#overrides.dompurify = 3.4.11` |

### Dependency chain

```
frontend
└── jspdf@4.2.1             (直接依赖，^4.2.1，npm 当前终版)
    └── dompurify@3.3.1..3.4.x (optional peer)
```

`npm explain dompurify` 标记为 optional：
```
optional dompurify@"^3.3.1" from jspdf@4.2.1
```

### Trigger path（历史）

- dompurify 仅在 jsPDF 解析 HTML 字符串（`jsPDF.html()`）时被调用，用来 sanitize 用户提供的 HTML。
- Prelude 不调用 `jsPDF.html()`，**仅调用 `jsPDF.addImage()`（canvas → image）**。

### Closing decision

通过 `package.json#overrides` 将 dompurify 强制钉到 `3.4.11`（npm 上已发布的 patch 版本，
覆盖 `<=3.4.10` 的全部已知 CVE）。npm audit 在本轮变更后返回 `found 0 vulnerabilities`。

### Next review condition

- 若审计出现新的 dompurify 高/严重级 CVE，且 overrides 无法覆盖，恢复台账登记。
- 若 jspdf 升级并自带 dompurify >= 3.4.11，可评估移除 overrides。
- 若未来切换到 `jsPDF.html()` 实现，重新评估 dompurify 实际触发路径。

---

## 风险 R-003 — TTS 池单线程吞吐瓶颈 — **保留观察**

| 字段 | 内容 |
|---|---|
| Audit base | b39eee1（首次登记时的 review commit） |
| Recorded in | 588bf73（首次登记），本轮仅做口径更新 |
| Status | 保留观察，需 session-level queue 设计后处理 |
| Severity | P3 (functional performance, not security) |
| Location | `backend/src/main/java/com/interview/config/ThreadPoolConfig.java#ttsTaskExecutor` |

### Current configuration

```java
@Bean("ttsTaskExecutor")
public Executor ttsTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(64);
    executor.setThreadNamePrefix("tts-");
    executor.initialize();
    return executor;
}
```

`VoiceInterviewTurnService` 已通过 `@Qualifier("ttsTaskExecutor")` 注入该 bean，
TTS 合成与 `CompletableFuture.allOf(...).get(TTS_AWAIT_SECONDS, TimeUnit.SECONDS)`
统一走该池；不再有 per-turn executor 生命周期管理。

### Trigger path

- `VoiceInterviewTurnService.processTurn` 在 LLM 流式输出期间，对每个完整 sentence 提交一次
  `synthesizeSentence` 到 `ttsTaskExecutor`，最后 `CompletableFuture.allOf(...).get(...)` 等待全部完成。
- 同一 turn 内多 sentence **提交顺序确定**，single-thread FIFO 保证 `sink.audio` 推送顺序与 sentence 顺序一致。
- `synthesizeSentence` 在调用 `sink.audio` 前再次检查 `ttsFailed` / `ttsTimedOut` flag，避免等待超时后迟到 audio 推给客户端。
- 30 秒 `TTS_AWAIT_SECONDS` 超时后置位 `ttsTimedOut`，`synthesizeSentence` 后续 sentence 跳过 `sink.audio` 调用。

### Why we are NOT bumping core/max here

- 把 `ttsTaskExecutor` 改为多线程会让**同一 turn** 的多个 sentence 并行合成。
  `sink.audio(base64)` 由不同线程并发推送，audio 顺序错位，用户听到 TTS 词序断裂。
- session-level 串行保护（每 session 一个独立串行队列 + 跨 session 并行）才是正确解，但需要新增队列/调度状态机。
- 按本轮硬性原则（不在本轮引入复杂队列、状态机或异步框架），不在本轮改动生产代码。

### Current mitigation

- Spring-managed `ttsTaskExecutor` 单线程 FIFO 池保证 turn 内音频顺序正确。
- queue=64 足够容纳单 turn 的 LLM 累积 sentence。
- `synthesizeSentence` 入口 + `sink.audio` 前双重 flag 检查，避免 timeout 后迟到 audio 推送给客户端。
- 跨 session 串行是已知 trade-off；当前并发量下未观察到明显卡顿。

### Next review condition

- 当真实多用户并发语音场景暴露 TTS 延迟 / 卡顿反馈时，再设计 session-level queue 方案。
- 设计落点建议：以 `sessionId` 为 key 的串行调度器（如 per-session `ExecutorService` + 懒加载清理），
  不要直接放大全局 ttsTaskExecutor。

---

## Reviewer Notes

- 本台账**不声称**当前浏览器主路径绝对安全，只声明 **当前代码路径不触发已知 CVE 的利用条件**。
- 任何引入 `axios` Node adapter、`jsPDF.html()`、或新的可选 sanitizer 依赖的 PR，都必须在 PR 描述中重新评估上述触发条件。
- `package.json#overrides` 维护在独立字段，升级 axios / jspdf 时需检查 overrides 是否仍然必要。