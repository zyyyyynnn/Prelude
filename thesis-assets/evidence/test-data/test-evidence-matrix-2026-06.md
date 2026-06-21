# 测试数据与论证证据矩阵（2026-06）

## 文件定位

本文件用于把当前测试证据映射到论文第三、四、五章的可写边界。它不是正文，不冻结引用编号，不生成图表。

当前主口径：`start-dev` + `start-docker` + dev fixture。旧 Demo Twin、4 月回环数据、8081/5174 仅作为历史对照，不再作为当前主叙述。

## 当前 active 证据入口

| 文件路径 | 证据类型 | 可支撑内容 | 当前状态 | 限制说明 |
| --- | --- | --- | --- | --- |
| `env-2026-06.md` | 环境与构建记录 | 表 5.1 测试环境、基础构建记录、一次 BYOK/RabbitMQ 功能链路 | active | 本地阶段性采集，不代表生产部署 |
| `functional-cases-2026-06.md` | 功能测试用例 | TC-01 ~ TC-12 功能、BYOK、fallback、质量门禁、seqNum | active | 语音和并发指标必须限制性描述 |
| `dev-fixture-2026-06.md` | dev fixture 与历史 API 对照 | 本地验收数据夹具与未实测性能边界 | active | 多数真实性能指标未实测 |
| `quality-gates-2026-06-19.md` | CI 自动化质量门禁与本地预检（`verify:ui` / `verify:tokens` / `verify:a11y` / `capture:visual`） | CI blocking + 本地可重复执行、UI 静态扫描、token schema、a11y critical-only、capture artifact-only | active | JaCoCo 为 report-only；`verify:ui` 不得等同全量视觉回归；`verify:a11y` 不得等同完整 WCAG 2 AA；`capture:visual` 不得等同像素 diff blocking gate |
| `database-table-dictionary-2026-06.md` | 数据库表字典 | E-R 图字段细节与表结构说明 | active | 补充图 3.2，不替代 DDL |
| `real-llm-api-2026-05-27-redacted.md` | 历史真实 API 记录 | 单次真实公网模型链路对照 | historical supplement | 不作为当前默认模型、性能基准或推荐依据 |
| `archive/demo-2026-04-25.md` | 历史 Demo Twin 数据 | 旧本机回环数据对照 | archive | 不代表当前运行模式 |
| `archive/env-2026-04-24.md` | 历史环境记录 | 4 月环境对照 | archive | 不作为当前环境口径 |

## 代码与实现证据入口

| 文件路径 | 证据用途 | 当前判断 |
| --- | --- | --- |
| `evidence/code-snippets/interview-sse-resume-context-2026-04-24.md` | SSE 与简历上下文实现证据 | 可用；使用前需与当前源码抽样核对 |
| `evidence/code-snippets/structured-output-resilience-2026-06-02.md` | Structured Output、限流、熔断等实现证据 | 可用；限流/熔断只能写机制，不写压测通过 |
| `evidence/code-snippets/frontend-streaming-stability-2026-06-05.md` | 前端流式渲染与音频 composable 证据 | 可用；不代表高并发实测 |
| `evidence/code-snippets/rabbitmq-report-queue-2026-06-13.md` | RabbitMQ 报告任务队列证据 | 可用；不得写成生产级可靠投递 |
| `evidence/code-snippets/security-performance-hardening-2026-05-31.md` | 旧正则评分与早期安全优化证据 | 已降权；评分解析以 Structured Output 证据为准 |

## 功能测试证据矩阵

| 模块 | 已有证据 | 是否可写入正文 | 推荐写法 | 禁止写法 |
| --- | --- | --- | --- | --- |
| 登录 / 鉴权 | `functional-cases`、`api.md`、后端测试 | 可写 | 系统具备注册、登录、JWT 鉴权和未登录拦截能力 | 支持高并发登录或多端强制下线压测通过 |
| 简历上传 / 解析 | `functional-cases`、`real-llm-api` 历史对照 | 可写 | 支持 PDFBox 文本提取，并将简历文本用于岗位与面试上下文 | 可百分百解析任意扫描件或复杂图文 PDF |
| 岗位匹配 | `functional-cases`、岗位模板数据 | 可写 | 根据简历信息与岗位模板辅助生成面试上下文 | 精准预测岗位胜任度 |
| 阶段化模拟面试 | `functional-cases`、stage/message 相关测试 | 可写 | 面试按破冰、技术、深挖、收尾阶段推进，并保留上下文 | 完全消除模型幻觉或异常输入影响 |
| SSE 流式响应 | `functional-cases`、SSE code snippets | 可写机制与单用户链路 | 后端通过 SSE 推送片段，前端逐步渲染并处理异常 | 上千并发 SSE 零掉帧 |
| 报告生成 / 分析 | `functional-cases`、`rabbitmq-report-queue`、ReportJobWorker 测试 | 限制性可写 | `/finish` 发布 RabbitMQ 任务，worker 生成报告并推送 `report_ready` | 生产级可靠投递、消息零丢失 |
| BYOK / Provider | `quality-gates`、`docs/byok-capability.md`、LlmRouter 测试 | 可写 | 支持用户级 OpenAI-compatible endpoint、API Key、模型发现与加密保存 | 任意模型故障都可无感切换 |
| fallback 边界 | LlmRouter 测试 | 可写 | 内置 provider 可 fallback；openai-compatible 失败显式暴露 | 用户 BYOK 失败后静默改用系统 Key |
| 语音 / TTS | `functional-cases`、VoiceInterviewTurnService 测试 | 限制性可写 | 语音链路具备容错、顺序保护和 timeout 单元测试 | 真实 ASR/TTS 低延迟性能已通过 |
| 质量门禁 | `quality-gates`、CI workflow | 可写 | CI 覆盖后端测试、前端 build、audit、BYOK/dark verify、Sentrux、diff check（PR 路径用 merge-base 取得 diff 起点）、`verify:ui` / `verify:tokens` / `verify:a11y` blocking；`capture:visual` 作为 artifact-only 上传 17 个场景 PNG | coverage threshold 已达标、架构完全正确、UI 全量视觉回归通过、`capture:visual` 像素 diff blocking、完整 WCAG 2 AA 达标 |
| 消息序号 / 阶段系统消息 | InterviewMessage/Stage/Judge 测试 | 可写 | seqNum 基于 latest max+1，系统消息统一入口，降低稀疏序列风险 | 并发场景绝对无冲突 |

## 性能与边界矩阵

| 测试项 | 当前证据 | 可写程度 | 边界 |
| --- | --- | --- | --- |
| 构建验证 | `env-2026-06.md`、`quality-gates-2026-06-19.md` | 可写 | 仅证明本地/CI 构建和测试流程可重复 |
| npm audit | CI 与 `quality-gates` | 可写 | 基于 npm advisory，不等同完整供应链审计 |
| JaCoCo | CI artifact | 可写 | report-only，无阈值 |
| BYOK 浏览器验证 | `verify:byok` | 可写 | mock API 自动化流程，不代表公网模型性能 |
| 暗色主题验证 | `verify:dark`（CI blocking） | 可写 | UI sanity check，不等同全量视觉回归 |
| UI guardrail / semantic sizing | `verify:ui`（CI blocking） | 可写 | UI 静态扫描与 semantic sizing 红线，不等同全量视觉回归，不证明所有页面无样式缺陷 |
| Token schema | `verify:tokens`（CI blocking） | 可写 | token schema 与 design-locked 值可在 CI 校验，不生成 CSS |
| A11y | `verify:a11y`（CI critical-only） | 可写 | axe-core 仅 fail critical violations；serious（color-contrast 等）记入 backlog，不等同完整 WCAG 2 AA |
| Visual capture | `capture:visual`（CI artifact-only） | 可写 | 提供 17 个场景 PNG artifact 供人工 review；不做像素 diff，不作为 blocking gate |
| 高并发压测 | 无 | 不可写成已完成 | 可在局限性中说明未开展 |
| 限流/熔断触发实测 | 无压测数据 | 只写机制 | 不写阻断率、吞吐或抗压曲线 |
| 真实 ASR/TTS 端到端性能 | 无 | 只写工程容错 | 不写低延迟指标 |
| RabbitMQ 可靠性 | 本地链路与单元测试 | 限制性可写 | 不写 DLQ/outbox/publisher confirm 已具备 |

## 第五章可写结论边界

| 可写内容 | 必须降调 | 禁止写入 |
| --- | --- | --- |
| 功能测试覆盖核心业务链路，TC-01 ~ TC-12 具备当前证据支撑 | 语音、限流、熔断、RabbitMQ 可靠性只写实现机制与本地验证 | 已完成生产环境、高并发、招聘效果或公网模型性能验证 |
| CI 门禁与本地预检可重复执行（`verify:ui` / `verify:tokens` / `verify:a11y` / `capture:visual` 均接入 CI） | JaCoCo 只生成覆盖率报告，不设置阈值；`verify:ui` 只证明 UI 静态扫描通过；`verify:a11y` 只 fail critical axe violations；`capture:visual` 只产出 artifact | 覆盖率已达标、架构已被完全证明、UI 全量视觉回归通过、完整 WCAG 2 AA 达标、`capture:visual` 像素 diff blocking |
| BYOK 设置流程可自动化验证 | verify 脚本使用 mock API | BYOK 真实公网性能稳定 |
| RabbitMQ 报告链路具备异步解耦和幂等保护测试 | 本地 Docker Compose 与单元测试范围 | 生产级可靠投递、消息绝不丢失 |

## 后续补证建议

| 补证项 | 优先级 | 是否阻塞阶段 3 | 说明 |
| --- | --- | --- | --- |
| 当前 UI 截图登记 | P1 | 否 | 若正文或答辩正式引用截图，需登记并确认来源 |
| 真实 ASR/TTS 端到端测试 | P2 | 否 | 只有准备详细写语音能力时需要 |
| 限流 / 熔断触发最小实测 | P2 | 否 | 可增强第五章，但不应伪造为高并发压测 |
| 高并发压测 | P3 | 否 | 非本科设计必要项；不写即可规避风险 |
