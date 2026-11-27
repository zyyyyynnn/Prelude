# 工程师上手路径

目标：在 60 分钟内建立产品、边界、状态与调用链地图，并完成一次可验证的本地面试请求。先读当前事实，再改代码；术语以 [GLOSSARY.md](../GLOSSARY.md) 为准。

## 60 分钟理解路径

| 时间       | 路径                                                                                                                                           | 产出                                                                          |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| 0–10 分钟  | [README](../README.md)、[术语表](../GLOSSARY.md)、[设计规范](../DESIGN.md)                                                                     | 说出 Account/Profile/Configuration、Artifact/Asset、Session/Turn/Stage 的区别 |
| 10–20 分钟 | [后端架构](backend/architecture.md)、[前端架构](frontend/architecture.md)                                                                      | 画出 16 模块、`app → features → shared` 与两个公共入口                        |
| 20–30 分钟 | [Interview 状态 ADR](adr/interview-stage-state.md)、[Retrieval ADR](adr/retrieval-after-commit.md)、[Asset ADR](adr/asset-durable-deletion.md) | 列出会话、阶段、待答、评估与异步删除的真源                                    |
| 30–40 分钟 | 按下一节启动基础设施、后端、前端                                                                                                               | 后端健康检查通过，登录页可访问                                                |
| 40–52 分钟 | 按“一次面试请求调用链”从 UI 追到 MySQL、SSE 与 Job                                                                                             | 能解释消息何时持久化、阶段何时推进、报告何时可见                              |
| 52–57 分钟 | 按“常见修改落点”选一条真实需求路径                                                                                                             | 知道首先改哪个模块、哪个公共入口与哪组测试                                    |
| 57–60 分钟 | 按“排障入口”定位日志、状态表与门禁                                                                                                             | 记录一条可复现验证命令与失败证据                                              |

## 启动顺序

### 1. 准备环境

在仓库根目录使用 PowerShell 7+。已有 `.env` 时不要覆盖：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d mysql redis rabbitmq versitygw
docker compose ps
```

等待 MySQL、Redis、RabbitMQ 与 VersityGW 健康。端口只绑定 `127.0.0.1`；默认凭据仅用于本地开发。

### 2. 启动后端

新开 PowerShell 窗口：

```powershell
mvn -f backend/pom.xml -Dspring-boot.run.profiles=dev spring-boot:run
```

确认 `http://127.0.0.1:8080/actuator/health` 可用。`dev` 会重置 `demo` 账户验收数据，不重置其他账户。

### 3. 启动前端

再开一个 PowerShell 窗口：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

访问 `http://127.0.0.1:5173`，使用 `demo / 123456` 登录。

### 4. 配置模型与可选能力

| 能力           | 启动条件                                                                          |
| -------------- | --------------------------------------------------------------------------------- |
| 文字模型       | 在设置中配置 DeepSeek 或受支持的自定义协议；开面时冻结 ModelExecutionSnapshot     |
| Embedding 检索 | 配置可用的 embedding provider；不可用时检索退回关键词评分                         |
| 语音           | 设置部署级 `OPENAI_API_KEY`，按需设置 `OPENAI_BASE_URL`；语音不使用聊天 BYOK 凭据 |
| OAuth          | 配置 Google/GitHub client id/secret；未配置时密码登录正常启动                     |
| 完整容器栈     | 可运行 `start-docker.bat`；本地双进程模式可运行 `start-dev.bat`                   |

## 一次面试请求调用链

### 开面：`POST /api/interview/start`

| 步骤 | 代码入口                                               | 结果                                                               |
| ---- | ------------------------------------------------------ | ------------------------------------------------------------------ |
| 1    | `InterviewSetup` → `startInterview` → `apiRequest`     | 提交 resumeId、positionId、JD、模型、思考深度与附件                |
| 2    | `InterviewController.start` → `StartInterview.execute` | 校验账户拥有的简历、岗位与未绑定附件                               |
| 3    | `ModelExecutionPort.freezeSnapshot`                    | 冻结模型配置；后续 Configuration 修改不影响本场                    |
| 4    | `StartInterview` 事务                                  | 写 `ongoing` 会话、绑定附件、写岗位 system prompt 与 `warmup` 阶段 |
| 5    | `RetrievalTransactions.afterCommit`                    | 提交后才异步 index 简历、JD 与附件文本                             |
| 6    | 前端导航 `/interview?session=<id>`                     | Query 读取会话快照，自动开始首轮提问                               |

### 回答：`POST /api/interview/{sessionId}/chat`（SSE）

| 步骤 | 代码入口                                               | 结果                                                                                |
| ---- | ------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| 1    | `InterviewAnswerComposer` → `useInterviewSession.send` | 创建乐观 Turn，并中止上一请求                                                       |
| 2    | `streamInterview` → `streamRequest`                    | 带 CSRF 与 Session Cookie 发起 SSE 请求                                             |
| 3    | `InterviewController.chat` → `SseSessionStream`        | 注册实时连接与心跳，捕获 account 与 auth session                                    |
| 4    | `StreamChatTurn` → `RunInterviewTurn.execute`          | 校验 `ongoing`，在会话行锁下写用户消息与 PENDING 评估，递增 `seq_num`               |
| 5    | `InterviewContextService`                              | 组装阶段 system prompt、检索 top-k、摘要窗口或最近对话                              |
| 6    | `ModelExecutionPort.stream`                            | 按冻结快照调用模型；每个 delta 发 SSE `message`                                     |
| 7    | `handleInterviewStreamEvent`                           | 将 delta 追加到乐观助手消息                                                         |
| 8    | `InterviewTurnCompletion.complete`                     | 同事务写助手消息、设置 `awaiting_answer=true`，按 `boundary_seq` 与配额决定是否推进 |
| 9    | 异步 judge / summary                                   | 保存 `SCORED/UNSCORED` 或保持 PENDING；SSE `judge` 更新评分                         |
| 10   | `useInterviewSession.onSuccess/onError`                | 成功后重取服务端快照；失败时同样重取并丢弃乐观状态                                  |

Session 在每个模型 delta 前重新校验；失效会终止流，而不是继续发送账户数据。

### 收尾：`POST /api/interview/{sessionId}/finish`

| 步骤 | 代码入口                  | 结果                                                                          |
| ---- | ------------------------- | ----------------------------------------------------------------------------- |
| 1    | `FinishInterview.execute` | 仅 `closing` 且无 PENDING 评估可继续                                          |
| 2    | 同一事务                  | 关闭阶段，CAS 领取 `ongoing → generating`，写耐久 Job 与 publication          |
| 3    | Spring Modulith           | 提交后将 `BackgroundJobRequested` 外发 RabbitMQ；失败由 publication 补投      |
| 4    | `ReportGenerateHandler`   | `JobWorker` 领取 attempt、维持 Lease，调用报告生成                            |
| 5    | `ReportJobCompletion`     | 同事务完成 Job CAS、写报告、更新 Analytics；失败保留可重试状态                |
| 6    | `ReportJobLifecycle`      | 耐久终态后发送 `report_ready` 或 error；前端同时每 2 秒重取 `generating` 会话 |

## 关键状态真源

| 问题             | 真源                                         | 不应使用                |
| ---------------- | -------------------------------------------- | ----------------------- |
| 当前登录主体     | Redis Auth Session + `CurrentAccount`        | 前端本地缓存            |
| 一场面试是否结束 | `interview_session.status`                   | SSE 是否断开            |
| 当前阶段         | `interview_stage.ended_at IS NULL`           | 消息时间戳推断          |
| 是否等待回答     | `interview_stage.awaiting_answer`            | 分散的组件布尔值        |
| 消息顺序         | `interview_message.seq_num` + 唯一键         | 数组下标或创建时间      |
| 回答是否已评分   | `assessment_status`                          | SSE `judge` 是否到达    |
| 报告是否生成     | `summary_report` + `finished` 状态           | `report_ready` 通知     |
| 检索是否可重建   | MySQL 简历/JD/附件元数据                     | 内存向量索引            |
| 对象是否已删除   | 对象存储确认 + `asset_deletion_request` 完成 | 业务引用消失            |
| 后台任务是否执行 | `background_job` / `job_attempt`             | RabbitMQ 消息是否曾投递 |

## 常见修改落点

| 需求                     | 首选落点                                                                       | 必须同步                                           |
| ------------------------ | ------------------------------------------------------------------------------ | -------------------------------------------------- |
| 登录、Session 或账户资料 | `identity`、`frontend/src/features/auth`、`settings` 视图                      | Auth 状态、CSRF/Origin、账户缓存隔离测试           |
| 模型 provider 或能力     | `llm.api` 窄 Port 与 `llm` adapter                                             | Configuration/Snapshot 语义、BYOK 与模型能力测试   |
| 开面字段或上下文         | `interview/application/StartInterview`、`InterviewSetupComposer`               | Request DTO、检索文档、快照、前端类型              |
| 消息或阶段规则           | `InterviewStageDefinition`、`InterviewStageManager`、`InterviewMessageService` | Flyway、状态 ADR、阶段/事务/并发测试               |
| 文字流或评分展示         | `useInterviewSession`、`handleInterviewStreamEvent`、`InterviewJudgeService`   | 乐观回滚、PENDING 重试、SSE 事件契约               |
| 语音                     | `voice`、`useVoiceInterview`、composer                                         | STT/TTS 降级、连接释放、Session 撤销、demo harness |
| 附件或删除               | `assets`、`AttachmentContextPort`                                              | READY 所有权、耐久删除意图、租约与 MySQL smoke     |
| 报告或 Analytics         | `artifact`、`InterviewReportPort`、`jobs`                                      | Job fence、报告事务、结构化报告与前端解析          |
| 公共 UI 组件             | `frontend/src/shared/ui/index.ts` 与具名实现                                   | `DESIGN.md`、token、architecture/UI/cascade 门禁   |
| 工具链或 CI              | `frontend/package.json`、`vite.config.ts`、`.github/workflows/ci.yml`          | lockfile、Oxc 验证、SBOM/Grype 与证据顺序          |

## 排障入口

| 现象                 | 首查                                                       | 处理方向                                                               |
| -------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------- |
| 启动连接失败         | `docker compose ps`、后端 actuator health                  | 先确认 13306/16379/5672/19000 服务，再查 `.env` 与 profile             |
| 登录返回 503         | 后端 `rate_limit_unavailable`、Redis 健康                  | 限流依赖不可用会 fail closed；恢复 Redis，不以关闭安全边界作为默认修复 |
| 登录后仍要求登录     | `/api/auth/me`、Session Cookie、`RequireAuth`              | 区分启动探测匿名与普通 API 401 过期；检查账户缓存是否清空              |
| 开面成功但没有上下文 | `retrieval_indexed`、`retrieval_rebuilt`、embedding 日志   | 确认提交后动作执行；embedding 失败会退回关键词                         |
| 删除后仍可检索到内容 | `retrieval_invalidate_*` 与 pending 日志                   | 失效未完成时 scope 应不可见；查 chunk 删除重试                         |
| 阶段不推进           | `interview_stage.boundary_seq/awaiting_answer`、助手消息数 | 检查配额、待答状态与 `ongoing` 门禁，不从时间戳猜阶段                  |
| 一直显示评估中       | PENDING 消息年龄、judge retry 日志                         | 查模型调用与重试调度；UNSCORED 会保存失败原因                          |
| 报告停在 generating  | Job 状态、attempt、lease、publication、RabbitMQ/DLQ        | Job 与 publication 是耐久真源；实时通知失败不等于生成失败              |
| 附件删除后对象仍存在 | `asset_deletion_request` 状态与 worker 日志                | PENDING 重试、RUNNING 租约、BLOCKED 所有权/key 冲突分别处理            |
| 语音退回文字         | `OPENAI_API_KEY`、WebSocket、麦克风权限、TTS 日志          | 文字链路继续；检查 3 秒 transport timeout 与 30 秒 TTS 等待            |
| 视觉门禁失败         | Playwright actual/diff、manifest、前端工作树               | 核对差异后更新基线/证据；不得手改 PNG 或哈希                           |

## 完成标准

- 能解释一次回答从 composer 到 `seq_num`、SSE、阶段推进与 judge 的完整路径。
- 能指出会话、阶段、评估、报告、检索、Asset 与 Job 的真源。
- 知道四个占位模块对应开放 Issue，Artifact 发布模型是无消费者的显式预留能力。
- 至少运行一项与改动相关的门禁；完整命令与 CI 顺序见 [本地开发](setup.md#验证)。
