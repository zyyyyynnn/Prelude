# 当前论文证据锁定索引

## 文件定位

本文件只登记当前仍有效、可追溯、可进入后续阶段审查的论文证据资产。它不是阶段过程报告，也不承载长篇实现说明。若与其他资产说明冲突，以 `workflow-governance.md` 为流程最高规范，以本文件列出的 active evidence 为事实入口。

## 当前有效证据资产

| 证据类型 | 当前路径 | 用途 | 当前状态 | 限制说明 |
| --- | --- | --- | --- | --- |
| 参考文献主库 | `thesis-assets/literature/references.bib` | 参考文献数据库 | 已收口 | 由 Zotero / Better BibTeX 管理 |
| 文献质量复核表 | `thesis-assets/literature/quality-review.md` | 文献质量审查依据 | 已收口 | 不冻结正文引用编号 |
| 文献证据映射表 | `thesis-assets/literature/evidence-map.md` | 文献到章节的证据映射 | 已收口 | 不等同正文引用顺序 |
| 最终参考文献编号锁定 | `thesis-assets/literature/final-reference-lock.md` | DOCX 整合前参考文献编号与正文引用闭环 | 已建立 / 待格式终审 | 不等同 references.bib 全量候选 |
| 最终参考文献正文源 | `thesis-assets/literature/final-references.md` | DOCX 整合前参考文献正文源 | 已建立 / 待格式终审 | 按 final-reference-lock 15 条候选生成，不等同 references.bib 全量候选 |
| 图表登记表 | `thesis-assets/evidence/figure-table-register.md` | 图表资产索引 | 已更新 | 图表进入正文前必须登记 |
| 绘图与模型资产 | `thesis-assets/evidence/diagrams/` | 图表源文件与导出图 | 图 3.3 已刷新 / 待复核 | 候选图 4.x 未冻结图号；图 3.3 尚未重新冻结 |
| 测试证据矩阵 | `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 测试数据与章节可写性映射 | 可用 | 当前主口径为 start-dev / start-docker / dev fixture；旧 Demo 数据仅作历史对照 |
| 功能用例 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 表 5.2 功能测试用例 | 可用 | 语音、压测和公网性能必须限制性描述 |
| 环境与构建记录 | `thesis-assets/evidence/test-data/env-2026-06.md` | 表 5.1 测试环境与第五章构建记录补充证据 | 可用 | 代表本地阶段性采集，不等同生产环境 |
| 质量门禁证据 | `thesis-assets/evidence/test-data/quality-gates-2026-07-13.md` | 两轮重构的 CI、本地验证与应用包覆盖率门禁快照 | 待用户与审查官复核 | 70% instruction coverage 只覆盖三个核心 application 包；2026-06-19 文件降为历史快照 |
| 数据库表字典 | `thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md` | 数据库表结构参考 | 可用 | 补充 E-R 图字段细节 |
| 代码片段证据 | `thesis-assets/evidence/code-snippets/` | 系统实现依据 | 可用 | 旧正则评分证据已被 Structured Output 证据替代；凡引用已删除 `InterviewServiceImpl` 路径的片段（SSE/评分/领域重构/RabbitMQ 旧生产者）仅 historical，正文不得直接引用，须先换 `finish-job-async-report-2026-07-13.md` 等新证据 |
| 模块化单体边界证据 | `thesis-assets/evidence/code-snippets/modular-monolith-boundary-hardening-2026-07-13.md` | 两轮重构后的模块与面试应用边界实现依据 | 待用户与审查官复核 | 只证明当前源码组织和依赖约束，不证明独立部署或运行时隔离 |
| 结束面试与异步报告任务证据 | `thesis-assets/evidence/code-snippets/finish-job-async-report-2026-07-13.md` | `/finish` → `FinishInterview` → `JobSchedulerPort` → `RabbitJobScheduler` → `ReportJobWorker` → `ReportGenerateHandler` 当前链路实现依据 | 待用户与审查官复核 | `ReportGenerateHandler→JobExecutionStore` 仍为过渡依赖；不证明 DLQ、outbox、publisher confirm 或生产可靠投递；旧 `rabbitmq-report-queue-2026-06-13.md` 降为 historical supplement |
| Bug 与修复证据 | `thesis-assets/evidence/bug-evidence/` | 精选问题复盘与答辩依据 | 可用（historical supplement） | Demo Twin 已退役，两文件均标 historical；仅作工程经验对照，不夸大为系统能力证明 |
| 阶段过程记录 | `thesis-assets/evidence/phase-reports/` | 审计和追溯 | 降权保留 | 顶层只保留 2.13 与 phase-3；其余历史过程报告归档至 `phase-reports/archive/`，不直接作为正文事实依据 |
| 答辩材料 | `thesis-assets/defense/` | PPT 映射与讲稿 | 可用 | 使用前仍需人工核对最新口径 |
| Phase 2.13 漂移同步报告 | `thesis-assets/evidence/phase-reports/phase-2.13-modular-monolith-sync-2026-07-13.md` | 两轮重构影响、资产变更与正文影响评估 | 待用户与审查官复核 | 不代表证据重新冻结或正文已同步 |
| 阶段 3 准备冻结报告 | `thesis-assets/evidence/phase-reports/phase-3-readiness-freeze-2026-06-20.md` | Final Evidence Freeze 与答辩准备核对 | 上次冻结入口 / 历史口径（已被 2.13 部分取代，质量门禁入口以 07-13 为准） | 只冻结证据口径，不代表正文已修改 |
| 历史测试数据归档 | `thesis-assets/evidence/test-data/archive/` | Demo Twin 时代与历史真实 API 记录 | historical supplement | 含 demo-2026-04-25、env-2026-04-24、quality-gates-2026-06-19、real-llm-api-2026-05-27；正文不得直接引用作为当前事实 |
| 历史阶段报告归档 | `thesis-assets/evidence/phase-reports/archive/` | 阶段 2.10 至 2.12、2.11 系列、pre-rewrite、ui-phase2 历史过程 | historical supplement | 含旧 InterviewServiceImpl、JaCoCo report-only、Demo Twin 等已退役口径；正文不得直接引用 |

## 当前锁定边界

- 当前证据索引只确认资产位置与用途，不代表正文已经完成同步。
- 当前已冻结基线仍为双字段：阶段 3 原始 freeze 审查基线 `4b2e967`；文档同步基线 `ffb617a`。两轮重构后的候选同步基线为 `851fa5b`，尚待用户与审查官复核，不构成自动重新冻结。
- 正文引用编号已完成最终连续化小修；DOCX/PDF 未生成。
- 新增证据必须先进入 active evidence 路径，再由用户和审查官复核。
- 不允许通过临时输出或外部生成物反向覆盖 `chapters/*.md`。

## 项目事实口径

- 当前运行入口：`start-dev.bat` 和 `start-docker.bat`。旧 Demo Twin、`start-demo`、`start-real`、8081/5174 仅属于历史归档或过程材料。
- 报告生成：`/finish` 经 `FinishInterview` 用例校验归属与状态后置 `generating`，再通过 `JobSchedulerPort` 发布任务；`RabbitJobScheduler` 适配 RabbitMQ 发布 `ReportJobMessage`，`ReportJobWorker` 监听并转交 `ReportGenerateHandler` 处理，完成后通过 SSE 推送 `report_ready`。应用层不再直接依赖 `RabbitTemplate` 或已删除的 `InterviewServiceImpl`。
- BYOK：OpenAI-compatible 支持用户级 endpoint、API Key、模型发现与运行模型选择；API Key 加密保存，具体运行模型不作为默认配置或论文推荐依据。
- Redis：承担限流、缓存和状态辅助职责。
- 质量门禁：CI 包含 whitespace diff check（PR 用 merge-base 取得 diff 起点）、Sentrux、后端测试、JaCoCo report 与三个核心 application 包 70% instruction coverage 检查、npm audit、前端 build、`verify:ui`、`verify:tokens`、`verify:byok`、`verify:dark`、`verify:a11y`；`capture:visual` 作为 artifact-only（`continue-on-error: true`）。
- 架构口径：当前后端是 Spring Boot 模块化单体，业务代码按模块和 API/application/domain/infrastructure 职责组织；面试 application 不导入本模块 API 包。该口径不表示模块可独立部署或应用层已完全框架无关。

## 写作限制

> 本段是论文写作限制的**唯一真相源**。其余索引文件（matrix / slide-map / register / guidance / phase-report / README）只允许链接到本段，不得复述以下限制清单。重复句（70%、非微服务、artifact-only、过渡依赖等）出现第二次即删，改链到本段。

- RabbitMQ 只能写成本地 Docker Compose 与单元测试支撑的异步报告链路，不得写成生产级可靠投递或消息零丢失。
- JaCoCo 可写成三个核心 application 包的 70% instruction coverage 阻断门禁，不得扩写为全仓覆盖率达到 70%。
- BYOK verify 是 mock API 浏览器自动化流程验证，不得写成真实公网模型性能验证。
- TTS 单元测试覆盖容错、顺序和 timeout，不得写成真实 ASR/TTS 低延迟性能基准。
- openai-compatible 失败必须显式暴露，不写成无感 fallback 到系统 provider。
- seqNum、ReportJobWorker 幂等和阶段系统消息测试可作为一致性证据，不得写成并发绝对无冲突。
- `verify:ui` 只能写成 UI 静态 guardrail 与 semantic sizing 红线扫描，不得写成全量视觉回归或 UI 完全无缺陷；`verify:ui` 是 CI blocking gate，只阻断新增违规。
- `verify:a11y` 是 CI critical-only gate，不得写成完整 WCAG 2 AA 达标。
- `capture:visual` 是 CI artifact-only（`continue-on-error: true`），不得写成像素 diff blocking gate 或视觉回归全覆盖。
