# Prelude 整体架构重写方案（目标态：最优可落地形）

| 字段 | 内容 |
| --- | --- |
| 状态 | 方案文档（未实施） |
| 目标态 | 模块化单体 + 领域边界清晰 + 平台内核可替换 |
| 部署形态 | **保持单后端进程 / 单前端 SPA**（不默认拆微服务） |
| 迁移策略 | 绞杀者（Strangler Fig）竖切迁移，禁止 Big Bang 停机重写 |
| 关联文档 | `docs/product/interview-main-flow.md`、`docs/api.md`、`docs/byok-capability.md`、`docs/quality/*`、`DESIGN.md` |
| 维护触发 | 域边界、数据真源、异步任务模型、Realtime 抽象或跨域契约变化 |
| 实施状态 | **未开工**；与代码不一致时以代码+产品文档为准，并回写本方案 |

---

## 1. 背景与问题定义

### 1.1 产品北极星（重写不得偏离）

Prelude 是**求职训练闭环**，不是聊天壳或岗位推荐站：

```text
简历资产（导入 / 制作 / 版本）
    → 目标岗位 + 可选 JD
    → 阶段化模拟面试（文字 SSE / 语音 WS）
    → 结构化报告 + 能力沉淀
    → 训练计划 / 简历补丁建议
    →（可选）再面试对比
```

产品主口径以 `docs/product/interview-main-flow.md` 为准；本方案只定义**如何把该闭环落到可持续演进的架构**。

### 1.2 现状结构（基线事实）

| 层 | 现状 |
| --- | --- |
| 后端形态 | Spring Boot 3.2 单体，`com.interview` 下 controller / service / mapper / llm / config |
| 前端形态 | Vue 3 + Vite SPA，路由：`/login`、`/interview`、`/resumes`、`/analytics` |
| 数据 | MySQL 表：`user`、`resume`、`position_template`、`interview_session/message/stage`、`score_history`、`user_weakness`、`llm_provider_config` |
| 实时 | 文字：`SseEmitter` + `SseEmitterRegistry`；语音：WebSocket handler + turn service |
| 异步 | RabbitMQ 报告任务（`ReportJobWorker`） |
| LLM | `LlmRouter` + 多 Provider + BYOK（AES-GCM）+ Resilience4j |
| 检索 | `SessionRagServiceImpl` + 进程内 `InMemoryVectorIndex` |
| 质量 | CI：编译/测试/`sentrux check`/UI guardrails/a11y/audit；JaCoCo 报告型覆盖率 |

### 1.3 结构性痛点（重写动机）

| ID | 痛点 | 表现 | 若不改的后果 |
| --- | --- | --- | --- |
| P1 | 编排中枢过重 | `InterviewServiceImpl` 同时承载 start/chat/finish/listen/SSE 细节 | 新功能继续堆中枢，回归成本指数上升 |
| P2 | 跨域直接耦合 | 面试编排直接依赖 `ResumeMapper` 等基础设施 | 简历域升级（文档真源/编辑器）被迫改面试核心 |
| P3 | 简历真源偏「文件文本」 | `rawText` + 部分 JSON 字段，缺统一 document 模型 | 简历制作、版本对比、报告反哺无法干净落地 |
| P4 | 检索生命周期弱 | 内存索引、重启依赖重建路径 | 多实例/可复现实验/稳定性叙事弱 |
| P5 | Realtime 与业务缠绕 | SSE/WS 细节散落在业务 service | 文字/语音双通道易漂移成两套业务 |
| P6 | 前端 feature 边界弱 | `InterviewView` / `InterviewComposer` / `AppSidebar` 体量大 | UI 质量体系再强也难单测与演进 |
| P7 | 平台能力未产品化 | LLM/RAG/Jobs 像「被业务调用的工具」而非内核契约 | Provider、计量、prompt 版本、任务可观测难统一 |

### 1.4 重写目标（最优可落地形）

1. **领域边界清晰**：Resume / Interview / Insight / Identity 互不直连对方持久化。
2. **平台内核可替换**：LLM、Retrieval、Jobs、Realtime 以 Port 暴露，实现可演进。
3. **数据真源明确**：简历以结构化 document 为真源；报告结构化 JSON 为真源；消息 append-only。
4. **文字/语音共用应用层用例**：Transport 可换，业务状态机唯一。
5. **可测可证**：核心 usecase 可单测；架构规则可 CI 阻断；关键路径有覆盖率门槛。
6. **保持部署简单**：默认仍 Docker Compose 单后端；不为「看起来像大厂」拆微服务。

### 1.5 非目标（明确不做）

- 默认拆成多微服务 / 多仓库 monorepo 强约束。
- 引入 MCP 作为主链路运行时依赖（与现有产品文档一致）。
- 重写 UI 视觉语言或推翻 `DESIGN.md` / UI quality system。
- 一次 PR 替换全部包名并停机迁移。
- 在本方案范围内追求 Zoom 级实时语音低延迟（语音以可靠降级与统一用例为先）。

---

## 2. 目标架构总览

### 2.1 逻辑视图

```text
┌──────────────────────────────────────────────────────────────────┐
│                         Client (Vue SPA)                         │
│  features: auth | resume | interview | insight | settings        │
│  shared: ui (DESIGN tokens) | api clients | lib                  │
└───────────────────────────────┬──────────────────────────────────┘
                                │ REST / SSE / WebSocket
┌───────────────────────────────▼──────────────────────────────────┐
│                    Prelude Application Host                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────┐ │
│  │  Identity   │  │   Resume    │  │  Interview  │  │ Insight │ │
│  │  Domain     │  │   Domain    │  │   Domain    │  │ Domain  │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────┬────┘ │
│         │                │                │               │      │
│  ┌──────▼────────────────▼────────────────▼───────────────▼────┐ │
│  │                    Platform Kernel                          │ │
│  │  LLM Gateway · Retrieval · Job Runtime · Realtime Hub       │ │
│  │  Security · Idempotency · Observability · Prompt Registry   │ │
│  └────────────────────────────┬────────────────────────────────┘ │
└───────────────────────────────┼──────────────────────────────────┘
                                │
        ┌──────────────┬────────┴────────┬──────────────┐
        ▼              ▼                 ▼              ▼
     MySQL          Redis           RabbitMQ      External LLM
   (真相源)     (会话/限流/缓存)    (异步任务)      (BYOK/内置)
```

### 2.2 部署视图（保持简单）

```text
[Browser]
   │
   ├─ Vite dev / nginx  →  Frontend static
   │
   └─ API/SSE/WS        →  Spring Boot (1 instance dev; N instance prod 需 sticky 或外部化 SSE registry)
                              ├─ MySQL
                              ├─ Redis
                              ├─ RabbitMQ
                              └─ Prometheus scrape (actuator)
```

**生产多实例约束（目标态必须写清）：**

| 组件 | 单实例 | 多实例目标态 |
| --- | --- | --- |
| SSE registry | 进程内 Map 可接受 | Redis pub/sub 或 sticky session + 文档声明限制 |
| RAG index | 进程内 + 可重建 | 共享 chunk 表 + 每实例本地缓存，或集中向量存储 |
| 报告 job | 多消费者靠 MQ | 保持；幂等键强制 |

本方案**第一优先**把「可外部化」的接口留好；**第二优先**再上共享实现。避免一上来引入未验证中间件。

### 2.3 架构原则（决策红线）

| # | 原则 | 落地要求 |
| --- | --- | --- |
| R1 | 模块化单体优先 | 域以 package 边界表达；进程内调用 + 事件，不默认 RPC |
| R2 | 依赖单向 | `adapter → application → domain`；domain 零框架依赖 |
| R3 | 跨域不碰库 | 禁止 `interview` 直接使用 `resume` mapper/entity |
| R4 | 用例驱动 | 每个用户意图对应一个 Application Service / UseCase 类 |
| R5 | 端口适配 | LLM/检索/时钟/事件总线皆为 Port，便于测试替身 |
| R6 | 兼容绞杀 | 旧 `*ServiceImpl` 可暂作 facade 委托新 usecase，逐步掏空 |
| R7 | 契约稳定 | 对外 REST/SSE 事件名优先兼容；破坏性变更走版本或双写期 |
| R8 | 质量门禁伴随迁移 | 每竖切合并必须测试不降、架构规则不破 |

---

## 3. 领域模型与边界

### 3.1 限界上下文

#### A. Identity（身份与偏好）

| 项 | 定义 |
| --- | --- |
| 职责 | 注册登录、JWT 主体、用户资料、主题偏好、会话用户上下文 |
| 聚合 | `User` |
| 应用用例 | `RegisterUser`、`LoginUser`、`UpdateProfile`、`GetCurrentUser` |
| 出站端口 | `UserRepository`、`PasswordHasher`、`TokenIssuer` |
| 现状映射 | `AuthController`、`UserController`（profile 部分）、`AuthService`、`UserProfileService`、`JwtInterceptor`、`UserContext` |

**注意：** BYOK 密钥与模型选择在目标态归 **LLM Platform 配置面**，Identity 只提供 `userId`；可用 Anti-Corruption 保留现有 `user` 表列，由 LLM 配置仓储读写，避免两套用户表。

#### B. Resume（简历资产）

| 项 | 定义 |
| --- | --- |
| 职责 | 简历导入、结构化文档真源、版本、投影文本、导出制品、（后续）模板渲染 |
| 聚合 | `Resume`（元数据）+ `ResumeDocument`（结构化内容）+ 可选 `ResumeRevision` |
| 应用用例 | `ImportResumePdf`、`CreateResumeDocument`、`UpdateResumeDocument`、`ListResumes`、`DeleteResume`、`GetResumeProjection`、`ExportResumePdf` |
| 出站端口 | `ResumeRepository`、`PdfTextExtractor`、`ResumeParser`（LLM 结构化）、`DocumentRenderer` |
| 入站端口（供他域） | **`ResumeContextPort`**：`requireOwnedProjection(userId, resumeId) → ResumeProjection` |
| 现状映射 | `ResumeController`、`ResumeServiceImpl`、`Resume` entity |

**`ResumeProjection`（跨域只读契约，稳定优先）：**

```text
resumeId
ownerUserId
displayName
plainText          # 面试/RAG 使用的文本投影
skills[]           # 可选摘要
projectsSummary[]  # 可选摘要
documentVersion    # 乐观并发 / 缓存失效
```

面试域**只依赖该投影**，不感知 PDF 或编辑器。

#### C. Interview（模拟面试）

| 项 | 定义 |
| --- | --- |
| 职责 | 会话生命周期、阶段、消息写入、一轮对话编排、结束转报告任务 |
| 聚合 | `InterviewSession`、`InterviewMessage`、`InterviewStage` |
| 应用用例 | `StartInterview`、`ListSessions`、`GetSessionMessages`、`UpdateStage`、`StreamChatTurn`、`ListenSession`、`FinishInterview` |
| 领域策略 | 所有权校验、状态机（ongoing/generating/finished）、阶段推进规则、`[STAGE_COMPLETE]` |
| 依赖 | `ResumeContextPort`、`PositionCatalogPort`、`ChatPort`、`RetrievalPort`、`RealtimePort`、`JobSchedulerPort`、`JudgePort` |
| 现状映射 | `InterviewController`、`InterviewServiceImpl`、`InterviewStageManager`、`InterviewContextService`、`InterviewMessageService`、`InterviewJudgeService`、voice* |

**状态机（目标显式化）：**

```text
ongoing ──finish──► generating ──report ok──► finished
                       │
                       └──report fail──► ongoing 或 failed（需产品二选一，默认：可重试 generating）
```

与现网字段对齐：继续使用 `interview_session.status` 字符串枚举，文档化合法迁移，避免魔法散落。

#### D. Insight（报告与能力画像）

| 项 | 定义 |
| --- | --- |
| 职责 | 报告生成、结构化装配、薄弱点/分数历史沉淀、分析查询 |
| 聚合 | `InterviewReport`、`ScoreHistory`、`UserWeakness` |
| 应用用例 | `GenerateInterviewReport`（job 内）、`GetReport`、`GetRadar`、`GetTrend`、`GetWeaknesses` |
| 依赖 | 读 Interview 消息/阶段（只读仓储或查询端口）、`ChatPort`（草稿）、`AnalyticsRepository` |
| 现状映射 | `ReportJobWorker`、`InterviewReportParser/Assembler`、`AnalyticsService`、`InterviewSummaryService` |

**不变式（来自产品主链路，重写必须保留）：**

- 逐题 score/hint 只来自已落库 user 消息。
- 阶段分/总分由已落库分数派生，LLM 草稿不得覆盖派生字段。
- 薄弱点以 `user_weakness` 为 Analytics 真源。

#### E. Catalog（岗位模板，可并入 Interview 或 Shared）

| 项 | 定义 |
| --- | --- |
| 职责 | 岗位模板列表与 system prompt |
| 用例 | `ListPositions`、`GetPosition` |
| 现状 | `PositionController`、`PositionService` |

目标态以 **`PositionCatalogPort`** 暴露给 Interview，避免 start 用例直接依赖 mapper。

#### F. Platform Kernel（非业务域，基础设施产品化）

| 子系统 | 职责 | 关键 Port |
| --- | --- | --- |
| LLM Gateway | 路由、BYOK、熔断、超时、计量、prompt 版本 | `ChatPort`、`EmbedPort`、`LlmConfigPort` |
| Retrieval | 分块、索引、混合检索、作用域失效 | `RetrievalPort` |
| Job Runtime | 投递、消费、重试、幂等、状态查询 | `JobSchedulerPort`、`JobHandler` |
| Realtime Hub | SSE/WS 连接注册、按 session 扇出 | `RealtimePort` / `SessionStreamSink` |
| Security | AES-GCM、JWT、限流拦截 | 现有组件收口 |
| Observability | 日志字段、metrics、（可选）trace id | 约定即可，不强制上全链路商业 APM |

---

## 4. 包结构目标态

### 4.1 后端 Java 包（目标树）

> 允许过渡期保留 `com.interview.service.impl.*` facade；目标树为终态。

```text
com.interview
├── InterviewBackendApplication
├── identity
│   ├── api              # AuthController, UserProfileController
│   ├── application      # LoginUser, RegisterUser, UpdateProfile
│   ├── domain
│   └── infrastructure   # MyBatis UserMapper 适配
├── resume
│   ├── api
│   ├── application
│   ├── domain           # ResumeDocument schema 对象
│   ├── infrastructure
│   └── api.port         # ResumeContextPort（也可放 application.port）
├── interview
│   ├── api              # InterviewController, VoiceWebSocket 入站适配
│   ├── application      # StartInterview, StreamChatTurn, FinishInterview...
│   ├── domain           # Session state, stage policy
│   └── infrastructure
├── insight
│   ├── api              # AnalyticsController；报告查询若独立可放此
│   ├── application      # GenerateReport, analytics queries
│   ├── domain
│   └── infrastructure   # ReportJobHandler 适配 MQ
├── catalog              # position templates（可 thin）
├── platform
│   ├── llm              # router, providers, config, metrics（由现 com.interview.llm 迁入）
│   ├── retrieval
│   ├── job
│   ├── realtime
│   └── security
├── shared
│   ├── api              # Result, error codes
│   ├── web              # GlobalExceptionHandler, interceptors 注册
│   └── util
└── bootstrap            # 仅组装配置、DevFixture（dev profile）
```

### 4.2 依赖矩阵（允许 / 禁止）

| From \ To | identity | resume | interview | insight | catalog | platform | shared |
| --- | --- | --- | --- | --- | --- | --- | --- |
| identity | ✓ | ✗ | ✗ | ✗ | ✗ | security/llm-config 有限 | ✓ |
| resume | 读 userId 上下文 | ✓ | ✗ | ✗ | ✗ | llm/retrieval 可选 | ✓ |
| interview | 读 userId | **仅 Port** | ✓ | 仅调度 job | **仅 Port** | llm/retrieval/realtime/job | ✓ |
| insight | 读 userId | 可选发 patch 事件 | **只读查询 Port** | ✓ | ✗ | llm/job | ✓ |
| platform | ✗ 业务 | ✗ 业务 | ✗ 业务 | ✗ 业务 | ✗ | ✓ | ✓ |
| api controllers | 只调本域 application | 同左 | 同左 | 同左 | 同左 | 配置类 API 可调 platform | ✓ |

**架构规则落地（CI）：**

- 保留并强化 `.sentrux/rules.toml`：`controller/* → mapper/*` 禁止。
- 增加（或用 ArchUnit 补充）：
  - `interview.application` 不得 import `resume.infrastructure` / `..mapper.ResumeMapper`
  - `*.domain` 不得 import `org.springframework.*`（可分阶段启用）

### 4.3 前端目录目标态

```text
frontend/src/
├── app/                     # main.ts 装配、App.vue 壳
├── features/
│   ├── auth/
│   ├── resume/              # 列表、上传、（未来）编辑器与模板预览
│   ├── interview/           # workspace、composer、stream、voice
│   ├── insight/             # report panels、analytics views
│   └── settings/            # profile / theme / llm panels
├── shared/
│   ├── ui/                  # 现有 components/ui 迁入或 re-export
│   ├── api/                 # contracts + http + 域 client
│   ├── lib/
│   └── composables/         # 跨 feature 极少；优先 feature 内
├── styles/
└── router/
```

**规则：**

- `features/interview` 不得直接改 `features/resume` 内部 store；通过 API 或显式事件。
- 大视图只做 layout 组合；副作用进 composable。
- UI 红线仍以 `DESIGN.md` + `verify:ui/tokens/a11y` 为准。

---

## 5. 关键用例序列（目标态行为）

### 5.1 开始面试 `StartInterview`

```text
Controller
  → StartInterview.handle(userId, resumeId, positionId, jdText?, llmModel?)
      → ResumeContextPort.requireOwnedProjection(...)
      → PositionCatalogPort.require(positionId)
      → 查重 ongoing session（同 user+resume+position）或创建 session
      → 写入 system 消息（岗位 prompt）
      → StagePolicy.ensureWarmup
      → Job/Async: RetrievalPort.index(sessionId, projection.plainText, jdText)
      → return sessionId + stage
```

**验收：** 与现网 start 语义兼容；无 ResumeMapper 出现在 interview application 源码。

### 5.2 文字一轮对话 `StreamChatTurn`

```text
Controller 打开 SSE
  → RealtimePort.register(sessionId, sink)
  → StreamChatTurn.handle(...)
      → 校验 ongoing + ownership
      → 持久化 user 消息
      → 异步 JudgePort.score(userMessageId)  （不阻塞主回复；不向前端展示实时分）
      → ContextBuilder:
            history + stage prompt + RetrievalPort.search(sessionId, query, topK)
      → ChatPort.stream(promptVersion, messages, selection)
      → 逐 token RealtimePort.publish(delta)
      → 持久化 assistant 消息
      → StagePolicy 检测 STAGE_COMPLETE 标签并推进
      → complete SSE
```

**验收：** 事件形状兼容现前端 `useInterviewTextStream`；dev fixture 路径仍可注入。

### 5.3 结束面试 `FinishInterview`

```text
  → 校验可结束（存在对话轮次等现规则）
  → status = generating
  → JobSchedulerPort.enqueue(REPORT_GENERATE, sessionId, idempotencyKey)
  → return jobId / status
  → Report handler（Insight）
        → 聚合消息/阶段/已落库分数
        → ChatPort 取叙述草稿（或 fixture）
        → Parser + Assembler（保留现不变式）
        → 写 summary_report / score_history / user_weakness
        → status = finished
        → RealtimePort.publish(report_ready)
```

### 5.4 语音回合（目标统一）

```text
WS 入站 adapter 解析事件
  → 同一应用命令：AppendUserUtterance / StreamAssistantReply
  → TTS 出站经 VoicePort（可失败降级为仅文本）
```

**禁止**再复制一套 session 状态机。现有 `VoiceInterviewTurnService` 目标变为 adapter + 少量语音特有编排。

---

## 6. 数据模型演进

### 6.1 原则

1. **可双读双写**：新列/新表先加，旧字段保留直至迁移完成。
2. **不做破坏性 rename 优先**：先扩展，后收敛。
3. **schema.sql + 迁移脚本**与 dev fixture 同步（产品文档已要求）。

### 6.2 目标表变更（建议）

#### `resume` 扩展

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `document_json` | JSON/LONGTEXT | `ResumeDocument` 真源 |
| `document_version` | INT | 每次更新 +1 |
| `source_type` | VARCHAR | `pdf_import` / `editor` / `fixture` |
| `plain_text_projection` | LONGTEXT | 可选缓存投影，避免每次渲染 |

`raw_text`、`parsed_skills`、`parsed_projects`：**迁移期保留**；投影优先 `document_json` → 失败则 fallback `raw_text`。

#### `async_job`（新建，推荐）

| 列 | 说明 |
| --- | --- |
| `id` | 任务 id（可与现 jobId 对齐） |
| `type` | `REPORT_GENERATE` / `RESUME_EMBED` / `RESUME_EXPORT`… |
| `payload_json` | 业务键 |
| `status` | queued/running/succeeded/failed |
| `idempotency_key` | 唯一 |
| `attempts` / `last_error` | 可观测 |
| `created_at` / `updated_at` | |

现网若 jobId 仅存在于响应与日志，目标态应可查询。

#### `retrieval_chunk`（新建，对应路线图 Phase 3 / 实现阶段 R1）

| 列 | 说明 |
| --- | --- |
| `scope_type` | `session` / `resume` |
| `scope_id` | |
| `ordinal` | |
| `content` | |
| `embedding` | BLOB/表外存储；或仅存 content，embedding 本地缓存 |
| `content_hash` | 失效重建 |

进程内索引变为 **缓存**；MySQL（或后续向量扩展）为可重建源。  
Phase 3 允许先做 R0（增强重建路径、暂不建表），但 DoD 必须写清「重启后可重建」；建表属于 R1 推荐默认目标。

#### `interview_report`（可选拆表）

长期可将 `summary_report` 迁到独立表，便于查询与版本；**非第一刀必须**。若保留 TEXT JSON，需文档化 schema version 字段：`schemaVersion` 写入 JSON 根。

### 6.3 ResumeDocument JSON Schema（最小集）

```json
{
  "schemaVersion": 1,
  "locale": "zh-CN",
  "profile": { "fullName": "", "email": "", "phone": "", "targetRole": "" },
  "summary": "",
  "skills": [{ "name": "", "level": "familiar|proficient|expert" }],
  "experiences": [{
    "company": "", "title": "", "start": "", "end": "",
    "bullets": [""]
  }],
  "projects": [{
    "name": "", "role": "", "techStack": [],
    "bullets": [""], "outcome": ""
  }],
  "education": [{ "school": "", "degree": "", "end": "" }],
  "extras": []
}
```

**投影算法（必须单测）：** 确定性模板拼接 → `plainText`；禁止依赖 LLM 做投影。

### 6.4 迁移步骤（数据）

| 步骤 | 动作 | 回滚 |
| --- | --- | --- |
| M1 | 加列/加表，全可空 | drop 新列 |
| M2 | 导入路径双写：PDF 解析仍写 raw_text，同时写 document_json（LLM parse 映射） | 停双写 |
| M3 | `GetResumeProjection` 优先 document | 改回 raw_text |
| M4 | 后台批处理历史 resume 回填 document_json | 忽略失败行并记账 |
| M5 | 只读校验报告；编辑器上线后 raw_text 降级为 artifact | — |

---

## 7. 平台内核详细设计

### 7.1 LLM Gateway

**目标接口：**

```text
ChatPort
  stream(ChatRequest) → Stream<Token>
  complete(ChatRequest) → String / Structured<T>

EmbedPort
  embed(String text) → float[]

LlmConfigPort
  resolveSelection(userId, optionalModelOverride) → LlmSelection
  get/save/test/discover（现 BYOK API 迁入）
```

**`ChatRequest` 必备字段：** `userId`、`purpose`（judge/chat/report/parse）、`promptId`+`promptVersion`、`messages`、`selection`、`timeout`、`maxTokens`。

**从现状迁移：**

| 现状 | 目标 |
| --- | --- |
| `LlmRouter` 大类 | 拆 `SelectionResolver` + `ProviderRegistry` + `ChatFacade` |
| Provider 实现 | 保留，统一错误映射 |
| BYOK | 仍 AES-GCM；密钥永不入事件与日志 |
| Resilience4j | 留在 gateway 内，业务 usecase 不感知注解细节 |

**Prompt Registry：** `classpath:prompts/{purpose}/{version}.md` 或 DB；会话/报告元数据记录 version，保证可复现。

### 7.2 Retrieval

**`RetrievalPort`：**

```text
index(scopeType, scopeId, documents[])
search(scopeType, scopeId, query, topK) → List<Chunk>
invalidate(scopeType, scopeId)
```

**实现阶段：**

| 阶段 | 实现 | 适用 |
| --- | --- | --- |
| R0（现状增强） | 内存索引 + DB 可重建文本 | 开发/单实例 |
| R1 | chunk 落库 + 启动/miss 重建 + 本地缓存 | 默认目标 |
| R2 | 向量存储或 pgvector 等 | 实验/多实例优化 |

**混合检索：** 保留现网思路（向量 + keyword），系数配置化（如 `retrieval.hybrid.vectorWeight=0.7`）。

### 7.3 Job Runtime

| 能力 | 要求 |
| --- | --- |
| 投递 | `enqueue(type, payload, idempotencyKey)` |
| 消费 | 每 type 一个 `JobHandler` |
| 幂等 | 同 key 不重复成功副作用 |
| 失败 | 有限重试 + 终态 failed + last_error |
| 通知 | 成功后 Realtime 事件（报告） |

现状 `RabbitTemplate` + `ReportJobWorker` 迁移为 `ReportGenerateHandler implements JobHandler`。

### 7.4 Realtime Hub

```text
RealtimePort
  register(sessionId, connectionId, sink)
  unregister(...)
  publish(sessionId, eventName, payload)
```

- SSE adapter：`SseEmitterRegistry` 迁入 platform.realtime
- WS adapter：voice handler 只转事件
- **多实例：** 接口预留 `RealtimeBus`；默认 Noop/Local

### 7.5 安全与配置

| 项 | 目标 |
| --- | --- |
| 鉴权 | JWT 拦截器保持；UserContext 只放 identity 声明 |
| 限流 | LLM 相关限流留在 gateway / interceptor |
| CORS | 配置模块不动 |
| 密钥 | 加密服务独立 `platform.security` |
| DevFixture | **仅 dev/local profile**；实现可依赖多域 application，但不进入 prod 类路径逻辑 |

---

## 8. API 与兼容策略

### 8.1 对外 API

**优先保持** `docs/api.md` 现有路径与 `Result` 包络：

- `/api/auth/**`、`/api/resume/**`、`/api/interview/**`、`/api/llm/**`、`/api/user/**`、`/api/analytics/**`

重写默认 **不改 URL**；内部换 usecase。

### 8.2 SSE 事件兼容

迁移期间禁止随意改事件名。若新增：

- 使用可忽略的新 event（前端未知则跳过）
- 或带 `schemaVersion`

### 8.3 破坏性变更流程

1. 双写/双读至少经历一个兼容发布版本  

2. 更新 `docs/api.md` + 前端 contracts  
3. 提供 fixture 与 verify 脚本  
4. 再删除旧字段语义  

---

## 9. 前端重写要点

### 9.1 状态归属

| 状态 | 归属 |
| --- | --- |
| token / 登录用户 | `features/auth` store |
| 简历列表与编辑草稿 | `features/resume` |
| 当前 session、消息流、stage | `features/interview` |
| 报告展示与 analytics 数据 | `features/insight` |
| BYOK 表单 | `features/settings`（可复用 `useLlmSettings`） |

### 9.2 面试工作台拆分映射

| 现状 | 目标模块 |
| --- | --- |
| `InterviewView.vue` | `features/interview/pages/InterviewPage.vue` 壳 |
| `InterviewComposer.vue` | `components/ComposerText.vue` + `ComposerVoice.vue` |
| `MessageThread.vue` | 保持；仅消费 view-model |
| `useInterviewTextStream.ts` | `features/interview/composables/useTextStream.ts` |
| `useReportListener.ts` | insight 或 interview 边界清晰的 report listener |
| `AppSidebar.vue` | `features/interview/components/SessionSidebar.vue`（会话列表）+ 导航壳 |

### 9.3 质量不回退

每阶段前端竖切必须：

```powershell
npm --prefix frontend run build
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:a11y
```

涉及设置流时追加 `verify:byok` / `verify:dark`。

---

## 10. 分阶段迁移路线图（可落地）

> 每阶段可独立合并到 `main`；阶段出口有明确 DoD。  
> **禁止**并行开启超过 2 条竖切（降低冲突）。

### Phase 0 — 约定与基线冻结

| 交付 | 说明 |
| --- | --- |
| 本文档评审通过 | 作为北极星 |
| 基线命令全绿 | `mvn test` + frontend verify 套件 |
| 风险台账对齐 | RAG 生命周期、SSE 多实例、TTS 串行（R-003） |
| 依赖禁令草案 | sentrux/ArchUnit 清单 |

**DoD：** 有「当前架构问题列表」与「目标依赖矩阵」被团队/个人确认。

### Phase 1 — 跨域端口与面试编排瘦身

| 步骤 | 内容 |
| --- | --- |
| 1.1 | 引入 `ResumeContextPort` + 默认实现（包可先放 `service.port`） |
| 1.2 | `StartInterview` 经 Port 取投影；Interview 去掉 `ResumeMapper` 依赖 |
| 1.3 | 抽出 `InterviewSessionQueryService`、`InterviewChatStreamer` |
| 1.4 | `InterviewServiceImpl` 变为 facade 委托 |
| 1.5 | 补/改单测：start、chat、listen、finish |

**DoD：**

- 行为兼容；测试全绿  
- interview 应用代码无 `ResumeMapper` import  
- `InterviewServiceImpl` 行数显著下降或职责仅编排  

### Phase 2 — LLM Gateway 与 Prompt 版本

| 步骤 | 内容 |
| --- | --- |
| 2.1 | `ChatPort`/`EmbedPort` 接口；`LlmRouter` 适配为实现 |
| 2.2 | Selection 解析拆分；Router 单测保留 |
| 2.3 | purpose 分类：chat/judge/report/parse |
| 2.4 | promptId/version 记录到日志与关键落库点（最小） |

**DoD：** 业务 usecase 只依赖 Port；BYOK 行为与 `docs/byok-capability.md` 一致。

### Phase 3 — Retrieval 可重建与可观测

| 步骤 | 内容 |
| --- | --- |
| 3.1 | `RetrievalPort` 统一 index/search/invalidate |
| 3.2 | miss 重建路径单测 + 结构化日志 |
| 3.3 | （推荐）`retrieval_chunk` 表或等价持久化文本块 |
| 3.4 | hybrid 权重配置化 |

**DoD：** 进程重启后首次 search 可重建；失败降级不阻断面试。

### Phase 4 — Insight 域与 Job 显式化

| 步骤 | 内容 |
| --- | --- |
| 4.1 | `GenerateInterviewReport` usecase 从 worker 析出 |
| 4.2 | `async_job` 表或等价状态查询 |
| 4.3 | Parser/Assembler 不变式加架构测试/单测锁定 |
| 4.4 | Analytics 只读 insight 查询服务 |

**DoD：** 报告不变式测试覆盖；job 状态可查；产品主链路文档仍成立。

### Phase 5 — Realtime 统一与语音适配

| 步骤 | 内容 |
| --- | --- |
| 5.1 | `RealtimePort` + SSE 实现迁移 |
| 5.2 | 文字 StreamChatTurn 只打 Port |
| 5.3 | Voice 入站改为调用同一应用命令 |
| 5.4 | TTS 失败降级路径测试（保留 R-003 观察） |

**DoD：** 语音/文字共享 session 与消息模型；无双状态机。

### Phase 6 — 前端 feature 化（可与 Phase 1–5 并行收尾）

| 步骤 | 内容 |
| --- | --- |
| 6.1 | 建 `features/*` 目录，渐进移动文件（re-export 防炸） |
| 6.2 | 拆 `InterviewView` / `InterviewComposer` |
| 6.3 | api client 按域切分，`contracts.ts` 可保留总表或拆 barrel |
| 6.4 | 视觉与 a11y 回归 |

**DoD：** 主路径 verify 全绿；面试页脚本体量下降。

### Phase 7 — 简历真源与扩展插槽收官（衔接产品能力）

| 步骤 | 内容 |
| --- | --- |
| 7.1 | `document_json` 双写与投影算法 |
| 7.2 | Resume application 完整 CRUD |
| 7.3 | 导入适配器与编辑器（产品功能）可开工 |
| 7.4 | （可选）报告 → Resume Patch 事件 |

**DoD：** 新面试 100% 走 projection；历史数据迁移策略执行并可统计成功率。

### Phase 8 — 包目录终态收敛与规则收紧

| 步骤 | 内容 |
| --- | --- |
| 8.1 | facade 删除或缩到 API 适配层 |
| 8.2 | 包迁入 `com.interview.{domain}` |
| 8.3 | sentrux equality 回升目标评估；domain 无 Spring 规则启用 |
| 8.4 | 覆盖率：核心包 blocking 门槛（建议 instruction ≥ 70% 于 interview/resume/insight application） |

**DoD：** 依赖矩阵可被工具检查；文档与代码一致；本方案状态改为「已达成 / 部分达成」修订。

---

## 11. 测试与质量策略

### 11.1 测试金字塔

| 层级 | 范围 | 工具 |
| --- | --- | --- |
| 单元 | domain 策略、投影算法、assembler 不变式、hybrid score | JUnit |
| 用例 | Start/Chat/Finish 与 Port mock | JUnit + Mockito |
| API | WebMvc 契约 | 现有 `*WebMvcTest` |
| 前端单测 | composable、schema、report parse | 建议引入 Vitest（Phase 6） |
| E2E/UI | a11y、byok、dark、visual artifact | Playwright 现网 |

### 11.2 每 PR 必跑

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:a11y
# 变更触及设置时
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
# 可用时
sentrux check E:\Prelude
```

### 11.3 覆盖率策略

| 阶段 | 策略 |
| --- | --- |
| Phase 0–3 | 保持全局 report-only，但核心 PR 禁止降低关键类覆盖 |
| Phase 4+ | application 包逐步 blocking |
| 禁止 | 用删除测试换「变绿」 |

### 11.4 架构测试用例示例（应写成真实测试）

1. `interview.application` 不依赖 `ResumeMapper`  
2. controller 不依赖 mapper 包  
3. 报告派生分不读取 LLM 草稿中的 overall 字段覆盖（若已有逻辑则固化）  
4. Resume 投影纯函数快照测试  

---

## 12. 风险、成本与回滚

### 12.1 主要风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| SSE 行为回归 | 面试主路径不可用 | 先抽 streamer 且锁定 ChatListen 测试；禁止改事件名 |
| 包迁移冲突 | 长期分支难合 | 短竖切、频繁合并、facade 过渡 |
| equality 指标下降 | CI 红 | 新文件贴合现有模式；测试基建去重 |
| 过早持久化向量 | 复杂度暴涨 | Phase 3 先可重建，R2 另立项 |
| 重构范围失控 | 无功能交付 | 阶段 DoD 强制冻结；产品功能仅 Phase 7+ |
| 多实例 SSE | 生产扇出错误 | 文档声明单实例限制直到 RealtimeBus 完成 |

### 12.2 回滚策略

- 每个 Phase 独立 PR；git revert 友好。  
- 数据迁移只加列；回滚应用版本即可忽略新列。  
- 双写开关用配置：`resume.projection.source=document|raw`。

### 12.3 范围取舍

完整 Phase 0–8 为**目标态全量**。若需优先「可扩展基线」，最小充分集为：

```text
Phase 0 → Phase 1 → Phase 3 → Phase 2 → Phase 6
```

其余 Phase 4/5/7/8 按产品节奏与风险优先级插入，不改变依赖方向与 DoD 含义。

---

## 13. 与后续「简历制作」功能的衔接

架构重写与产品功能的关系：

```text
Phase 1  ResumeContextPort          ─┐
Phase 7  document_json + 投影        ├─► 简历编辑器 / Kami-inspired 模板导出
Insight  ReportReady 事件（可选）    ─┘     Resume Patch 闭环
```

**约束：**

- 未完成 `ResumeContextPort` 前，不在 `InterviewServiceImpl` 内嵌编辑器逻辑。  
- 模板渲染属于 Resume 域 `DocumentRenderer`，不属于 Interview。  
- 设计体系可参考外部作品（如 Kami）的「约束化排版」思想，但模板与字体授权独立治理，不把外部 skill 运行时打进主服务。

---

## 14. 决策记录（ADR 摘要）

| ADR | 决策 | 理由 |
| --- | --- | --- |
| ADR-1 | 模块化单体，不默认微服务 | 体量、运维、毕设/作品复杂度不匹配 |
| ADR-2 | 绞杀迁移，不 Big Bang | 降低主路径回归风险 |
| ADR-3 | 跨域仅 Port/事件 | 保护简历与面试独立演进 |
| ADR-4 | 简历 document 为真源，raw_text 过渡 | 支持制作/版本/投影 |
| ADR-5 | 文字语音共用应用用例 | 防止双业务栈 |
| ADR-6 | 报告派生分不变式保留 | 产品可信度与 Analytics 一致 |
| ADR-7 | Retrieval 先可重建再共享存储 | ROI 最优 |
| ADR-8 | 对外 API 尽量不变 | 前端与文档成本可控 |

---

## 15. 完成定义（目标态总 DoD）

当且仅当以下全部满足，可宣布「整体架构重写达标」：

1. 依赖矩阵被 CI 规则执行（至少 controller→mapper、interview↛ResumeMapper）。  
2. 面试/简历/报告/分析应用边界清晰，无新的上帝类扩张。  
3. `ResumeProjection` 为面试唯一简历入口；document 真源已上线，或迁移策略已落地且双写完成。  
4. LLM/Retrieval/Job/Realtime 均以 Port 使用。  
5. 报告不变式与主链路产品文档一致，fixture 同步。  
6. 前端主 feature 目录落地，面试主页面体量受控。  
7. 核心 application 测试与覆盖率门槛生效。  
8. `docs/api.md`、本方案、`risk-register` 与实现一致。  

---

## 16. 建议执行顺序（最短路径到「可扩展最优态」）

若资源有限，按收益排序的**最小最优路径**：

```text
Phase 0
  → Phase 1（端口 + 面试瘦身）     ★ 必须
  → Phase 3（检索可重建）           ★ 必须
  → Phase 2（LLM Port 化）          ★ 强烈建议
  → Phase 6（前端拆分）             ★ 强烈建议
  → Phase 4（Job/Insight 显式化）
  → Phase 5（Realtime/语音统一）
  → Phase 7（简历真源 → 产品）
  → Phase 8（收敛收紧）
```

---

## 17. 文档维护

| 变更类型 | 更新本文件 | 同步更新 |
| --- | --- | --- |
| 域边界/Port 变更 | ✓ | `product/interview-main-flow.md`（若用例变） |
| 表结构变更 | ✓ §6 | `schema.sql`、fixture、api 若暴露 |
| API 路径变更 | ✓ §8 | `api.md`、前端 contracts |
| 质量门禁变更 | ✓ §11 | `quality/*`、CI workflow |
| 风险变化 | 链接 | `quality/risk-register.md` |

---

## 附录 A — 现状 → 目标类映射表（摘录）

| 现状 | 目标 |
| --- | --- |
| `InterviewServiceImpl` | facade → `StartInterview` / `StreamChatTurn` / `FinishInterview` / query service |
| `InterviewStageManager` | `interview.domain` StagePolicy + application 调用 |
| `InterviewContextService` | ContextBuilder（application 或 domain service） |
| `InterviewJudgeService` | `JudgePort` 实现，chat 用例异步调用 |
| `InterviewReportParser/Assembler` | `insight.domain` + `GenerateInterviewReport` |
| `ReportJobWorker` | `platform.job` consumer → insight handler |
| `SessionRagServiceImpl` | `platform.retrieval` 实现 |
| `LlmRouter` | `platform.llm` Gateway |
| `SseEmitterRegistry` | `platform.realtime` LocalRealtimeHub |
| `ResumeServiceImpl` | `resume.application` Import/List/Delete + 后续 Document |
| `VoiceWebSocketHandler` | interview.api adapter |
| `DevFixtureService` | bootstrap.dev，实现多域 seeding |

## 附录 B — 配置项草案（目标）

```yaml
prelude:
  resume:
    projection-source: document # document | raw
  retrieval:
    hybrid:
      vector-weight: 0.7
      keyword-weight: 0.3
    persist-chunks: true
  realtime:
    mode: local # local | redis  (未来)
  jobs:
    report:
      max-attempts: 3
  llm:
    default-prompt-version:
      chat: v1
      judge: v1
      report: v1
      parse: v1
```

## 附录 C — 自检清单（实施前/每阶段出口）

- [ ] 是否仍保持单部署单元假设？  
- [ ] 是否出现跨域直接 mapper 依赖？  
- [ ] 对外 API/SSE 是否无意破坏？  
- [ ] dev fixture 是否与报告 schema 同步？  
- [ ] 失败路径（LLM/检索/TTS）是否降级而非整链路崩溃？  
- [ ] 测试与 verify 是否全绿？  
- [ ] 风险台账是否更新？  
- [ ] 是否把「产品功能」错误塞进平台层？  

---

**文档结束。** 实施时以 Phase DoD 为准绳；本方案是北极星与施工图，不是一次性 PR 的 diff 范围。


