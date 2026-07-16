# 当前论文证据状态索引

本文件是当前实现事实、有效证据和写作限制的唯一入口。目录导航见 `../README.md` 与 `../evidence/README.md`，历史材料见 `../evidence/**/archive/`。

## 当前基线

| 基线 | 提交或入口 | 状态 |
| --- | --- | --- |
| 当前项目实现 | `391171607d553a059c5aedb6baa69cd6d9148ac8` / PR #27 | 已合并至 `main`；主线 CI run `29471168463` 的 `schema` 与 `build` 均通过 |
| 最近一次论文正文同步 | `f064390` | 不包含当前代码基线变化，不得视为当前代码的正文映射 |
| 论文交付治理 | `9ad17ad` | Word/WPS 人工终审与证据先验边界生效 |
| 当前证据集 | `thesis-assets/evidence/` 的 2026-07-15 入口 | 候选待用户与审查官复核 |
| 正式证据冻结 | - | 尚未冻结；本地 Agent 无权自行判定通过 |

## 当前候选证据

| 类别 | 路径 | 支撑内容 |
| --- | --- | --- |
| 证据矩阵 | `thesis-assets/evidence/test-data/test-evidence-matrix.md` | 第三至第五章的证据映射 |
| 功能测试 | `thesis-assets/evidence/test-data/functional-cases-2026-07-15.md` | TC-01 至 TC-15 |
| 质量门禁 | `thesis-assets/evidence/test-data/quality-gates-2026-07-15.md` | 本地测试、CI、覆盖率与质量门禁 |
| 环境与数据 | `thesis-assets/evidence/test-data/environment-2026-07-15.md`、`thesis-assets/evidence/test-data/database-table-dictionary-2026-07-15.md`、`thesis-assets/evidence/test-data/dev-fixture-2026-07-15.md` | 环境、数据库和 local/dev 验收数据 |
| 检索容量 | `thesis-assets/evidence/test-data/retrieval-capacity-2026-07-15.md` | 有限规模合成容量结果 |
| 实现证据 | `thesis-assets/evidence/code-snippets/evidence-driven-training-and-safety-2026-07-15.md` | 简历闭环、BYOK、安全、检索、作业恢复与边界 |
| 图表资产 | `thesis-assets/evidence/figure-table-register.md`、`thesis-assets/evidence/diagrams/` | 图表来源、准入状态和同步导出文件 |
| 文献资产 | `thesis-assets/literature/` | 文献主库、质量复核、章节映射和最终编号 |
| 答辩资产 | `thesis-assets/defense/` | 上次冻结状态下的讲稿与页级证据映射，尚未按当前候选证据同步 |

## 当前项目事实

- 运行入口为 `start-dev.bat` 和 `start-docker.bat`；dev fixture 只用于 local/dev 验收。
- 后端是 Spring Boot 模块化单体，API、application、domain、port 与 infrastructure 依赖边界由架构测试约束。
- 简历改进由报告生成候选建议，服务端校验字段白名单、候选人原文证据、用户归属、原文与版本 CAS；只有用户逐项接受后才写入。
- `/finish` 通过作业端口和 RabbitMQ 适配器进入 `ReportJobWorker`；PENDING 超时与 RUNNING 租约过期可补投，错误落库前截断脱敏。
- LLM 配置由默认 DeepSeek 与 OpenAI Responses、OpenAI Chat Completions、Anthropic Messages 三种自定义协议组成；API Key 加密保存，自定义协议失败不回退系统 Provider。
- 自定义 LLM 出站请求执行 HTTPS/端口、URL、DNS、公网地址、重定向、超时和响应大小约束，并在连接时复验 DNS。
- 检索 chunk、内容哈希和 embedding 快照持久化；关键词与向量在完整候选集融合，查询 embedding 故障时退化到关键词。
- `schema.sql`、`data.sql`、`data-dev.sql` 分别是结构、生产安全数据维护和开发账号的唯一 SQL 入口；旧结构与旧 Provider 数据由幂等 SQL 原地收敛。
- CI 包含 whitespace、Sentrux、MySQL 8.4 schema/legacy/idempotency、后端测试与 JaCoCo、npm audit、前端 check/build、架构、契约、flows、UI、token、BYOK、dark 与 a11y 门禁；visual 作为截图证据。
- 图3.2 与图3.3 的 Mermaid 源、SVG 和 4x 白底 PNG 同步；当前 PNG 分辨率分别为 `3136x1780` 与 `3136x3048`。

## 写作限制

- 模块化单体不得表述为微服务或可独立部署模块。
- RabbitMQ 和作业恢复证据不支持生产级可靠投递、零丢失、DLQ、outbox、publisher confirm 或多实例强一致结论。
- JaCoCo 70% instruction coverage 门禁只覆盖配置的 application 包，不代表全仓覆盖率达到 70%。
- BYOK 自动化验证使用 mock API，不代表真实公网模型性能或任意 endpoint 兼容性。
- 合成检索容量结果不代表真实语义标注集质量、生产级 RAG、并发容量或 SLO。
- 语音证据只覆盖工程容错、顺序保护和 timeout，不支持真实 ASR/TTS 性能结论。
- seqNum、作业幂等和阶段系统消息测试不支持“并发绝对无冲突”的结论。
- `verify:ui` 与 `verify:tokens` 是静态规则门禁，不等同全量视觉回归。
- `verify:a11y` 只阻断 critical；已知 serious 对比度项不等同完整 WCAG 2 AA 合规。
- visual 只验证截图生成和人工审查资产，不是像素差异阻断测试。

## 正文同步边界

当前候选证据覆盖实现事实和图表资产；`thesis-assets/chapters/**` 与 `thesis-assets/defense/**` 仍对应上次冻结状态。只有候选证据经用户与审查官复核并明确冻结后，才能按治理流程单章同步正文。
