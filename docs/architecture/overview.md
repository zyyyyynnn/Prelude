# Prelude 架构总览（当前态）

| 字段 | 内容 |
| --- | --- |
| 状态 | 当前基线（模块化单体已落地） |
| 部署 | 单后端进程 + 单前端 SPA；不默认微服务 |
| 关联 | `contributing-boundaries.md`、`docs/product/interview-main-flow.md`、`docs/api.md`、`docs/quality/risk-register.md` |
| 维护触发 | 域边界、跨域 Port、平台内核契约或前端 feature 边界变化 |

---

## 1. 逻辑结构

```text
Vue SPA (features/*)
        │ REST / SSE / WS
        ▼
┌ identity ─ resume ─ interview ─ insight ─ catalog ┐
│              application use cases                 │
│              domain (无 Spring)                    │
└──────────────┬─────────────────────────────────────┘
               ▼
        platform: llm · retrieval · job · realtime · security
               ▼
        MySQL / Redis / RabbitMQ / External LLM
```

### 后端根包

| 包 | 职责 |
| --- | --- |
| `identity` | 认证、用户资料、主题偏好 |
| `resume` | 简历导入、document 真源、投影、列表删除 |
| `interview` | 会话、阶段、文字 SSE、语音 WS、结束投递 |
| `insight` | 报告生成、能力画像、Analytics 查询 |
| `catalog` | 岗位模板 |
| `platform` | LLM / Retrieval / Job / Realtime / Security |
| `shared` | `Result`、异常、Web 装配 |
| `bootstrap` | 启动与 dev fixture（非 prod 业务） |

### 跨域只经 Port（摘要）

| 调用方 | Port | 提供方 |
| --- | --- | --- |
| interview | `ResumeContextPort` | resume |
| interview | `PositionCatalogPort` | catalog |
| insight | `InterviewReportPort` | interview |
| interview finish | `JobSchedulerPort` | platform.job |
| 多域 | `ChatPort` / `EmbedPort` / `RealtimePort` / `RetrievalPort` | platform |

文字与语音共用 `RunInterviewTurn`；transport 适配不得复制会话状态机。

---

## 2. 数据真源

| 数据 | 真源 |
| --- | --- |
| 简历 | `document_json`（投影失败可 fallback `raw_text`） |
| 消息 | `interview_message` append-only |
| 报告 | `summary_report` 结构化 JSON + markdownFallback |
| 检索块 | `retrieval_chunk` + 进程内缓存 |
| 异步任务 | `async_job` + RabbitMQ 投递 |

产品不变式（评分派生、薄弱点来源等）以 `docs/product/interview-main-flow.md` 为准。

---

## 3. 前端结构

| 路径 | 职责 |
| --- | --- |
| `frontend/src/features/*` | 业务 feature（auth / resume / interview / insight / settings） |
| `frontend/src/shared` 等价物 | 现阶段 `components/ui`、`api/http`、`api/contracts` 等共享层 |
| `frontend/src/api/*`、`composables/*` | **兼容 re-export**，新代码优先直接引用 `features/*` |

路由入口已指向 `features/*/pages`。

---

## 4. 质量与风险

- 架构规则：`.sentrux/rules.toml` + `ArchitectureBoundaryTest`
- 核心 application 覆盖率：JaCoCo 门槛（见 `backend/pom.xml`）
- 前端：`verify:ui` / `tokens` / `a11y` / `byok` / `dark`
- 残留风险：`docs/quality/risk-register.md`（Realtime 单实例、Job 补投、简历回填等）

---

## 5. 扩展指引

- **简历制作 / 模板导出** → 只进 `resume` 域，面试只消费 `ResumeContextPort` 投影。
- **新 LLM Provider** → `platform.llm`，业务 usecase 只认 `ChatPort`/`EmbedPort`。
- **报告反哺简历** → insight 发建议或调用 resume 入站 Port，禁止 insight 写 resume 表。
- **贡献规则** → 见 `contributing-boundaries.md`。
