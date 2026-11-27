# Prelude 术语表

术语统一以领域所有权和持久化真源为准；对外 API 名称可以在不改变领域含义的前提下保留兼容称谓。

## Account / Profile / Configuration

| 术语                   | 定义                                                                 | 真源与边界                                                               |
| ---------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Account                | 登录主体与资源所有权边界；所有用户资源以 `accountId` 隔离            | `identity.user_account`；领域统一用 `Account`，对外兼容称谓可保留 `User` |
| Profile                | Account 的描述性资料与账户级偏好，不属于认证凭据或模型执行配置       | `identity` 拥有； revision/expectedRevision/operationId 控制并发         |
| Configuration          | 对外的 provider/model 配置读写模型，描述下一次模型调用使用的可变参数 | `llm` 拥有；不得复制到 `settings` 占位模块，也不回写已开始面试           |
| ModelExecutionSnapshot | 开面时冻结的模型、provider 与思考深度配置                            | `llm` 持久化；运行中的回合只读取快照，Configuration 修改只影响后续快照   |

## Artifact / Asset

| 术语            | 定义                                                                                | 真源与边界                                                                            |
| --------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Artifact        | 可版本化的正式业务成果，例如面试报告或简历导出；逻辑身份由 `(accountId, kind)` 确定 | `artifact` / `artifact_version`；只能经 `ArtifactCommandApi` 发布；当前是显式预留能力 |
| ArtifactVersion | 已发布的不可变成果版本；新内容创建下一版本                                          | `artifact_version`；递增版本号、可选 Asset 引用与业务 provenance                      |
| Asset           | 二进制对象及其元数据，例如附件、头像或报告文件                                      | `asset` + S3 兼容对象存储；状态仅 `PENDING_UPLOAD/READY`，按 `accountId` 隔离         |
| Attachment      | 绑定到 Interview 等业务 scope 的 Asset 元数据与提取文本                             | `attachment.asset_id` 引用 Asset；删除业务引用不等于物理对象已删除                    |

Artifact 不是 Asset：前者是正式成果与版本模型，后者是二进制载体。Artifact provenance 使用业务 ID/revision，不使用内容哈希替代。

## Session / Turn / Stage

| 术语             | 定义                                                                 | 真源与边界                                                                                |
| ---------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Auth Session     | 浏览器与服务器之间的认证会话                                         | Spring Session Redis；无 MySQL Session 表；支持 rotation、logout revoke 与 session revoke |
| InterviewSession | 一场面试的业务聚合，拥有账户、简历、岗位、模型快照、消息、阶段与报告 | `interview_session`；生命周期为 `ongoing/generating/finished`                             |
| Turn             | 一次用户回答及对应助手回答的处理单元；可触发评估与摘要               | 持久消息落在 `interview_message`；实时 SSE/WebSocket 只是传输，不是 Turn 真源             |
| Stage            | InterviewSession 内的显式面试阶段                                    | `interview_stage`；顺序为 `warmup → technical → deep_dive → closing`                      |
| Stage boundary   | 当前阶段第一条消息之后的序号边界                                     | `interview_stage.boundary_seq`；自动推进只统计其后的助手消息                              |
| Awaiting answer  | 当前阶段是否已有助手提问但尚无用户回答                               | `interview_stage.awaiting_answer`；与消息追加同事务更新                                   |

## Snapshot

| 术语                                                                         | 定义                                          | 使用边界                                                 |
| ---------------------------------------------------------------------------- | --------------------------------------------- | -------------------------------------------------------- |
| ModelExecutionSnapshot                                                       | 不可变的模型执行配置                          | Configuration 变更不追溯已运行回合                       |
| InterviewSessionSnapshot / InterviewStageSnapshot / InterviewMessageSnapshot | 跨模块只读投影                                | 防止其他模块依赖 `interview.domain` 或内部仓储           |
| Retrieval snapshot                                                           | 某一 scope 的 chunk、embedding 与内容哈希集合 | 派生状态；可从 MySQL 中已提交的简历、JD 与附件元数据重建 |

Snapshot 表示某一时刻的冻结值或投影；只有模型执行快照是配置真源，检索快照不是业务真源。

## Job / Lease

| 术语              | 定义                                                                    | 真源与边界                                                          |
| ----------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Job               | 耐久后台执行单元，拥有类型、账户、subject、operationKey、状态与 attempt | `background_job` / `job_attempt`；报告任务通过 `JobDispatcher` 请求 |
| Claim             | 对 PENDING Job 的原子领取                                               | 成功后进入 RUNNING 并产生 attemptNumber                             |
| Lease             | Worker 对 Job 或 Asset 删除请求在限定时间内的执行所有权                 | 用心跳续期；过期后可回收；完成与重试必须匹配当前 attempt fence      |
| ExecutionLease    | 一次执行期间维持 Job 租约的句柄                                         | 在 try-with-resources 生命周期内关闭                                |
| Event publication | 与业务事务一起提交的 Modulith 事件记录                                  | `EVENT_PUBLICATION`；提交后外发 RabbitMQ，未完成记录有界补投        |

## Evidence

| 术语                   | 定义                                              | 真源与边界                                                                                   |
| ---------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Visual Evidence        | 前端输入修订与 surface PNG 的回归证据             | `docs/screenshots/manifest.json`；`inputRevision` 绑定前端内容，`surfaceHashes` 绑定图像字节 |
| Assessment evidence    | 支撑回答评分的题目、回答与评分理由                | `interview_message` 的 score/hint/assessment status 与报告投影；不是视觉证据                 |
| Retrieval content hash | 单个检索 chunk 的 UTF-8 SHA-256                   | `retrieval_chunk.content_hash`；用于内容标识与诊断，不用于 Artifact provenance               |
| CI evidence            | SBOM、漏洞扫描、Playwright actual/diff 与差异报告 | GitHub Actions artifacts；门禁结论以 Checks/Actions 为准                                     |

## 状态值速查

| 状态面                 | 值                                                                                               |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| InterviewSession       | `ongoing`、`generating`、`finished`                                                              |
| InterviewStage         | `warmup`、`technical`、`deep_dive`、`closing`                                                    |
| Assessment             | `PENDING`、`SCORED`、`UNSCORED`                                                                  |
| Asset                  | `PENDING_UPLOAD`、`READY`                                                                        |
| Asset deletion request | `PENDING`、`RUNNING`、`BLOCKED`                                                                  |
| Auth                   | `checking`、`authenticated`、`anonymous`、`unavailable`；`expired` 表示普通 API 401 后的失效提示 |
