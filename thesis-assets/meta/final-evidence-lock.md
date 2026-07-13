# 当前论文证据状态索引

本文件是当前实现事实、有效证据和写作限制的唯一入口。目录导航见 `../README.md` 与 `../evidence/README.md`，历史过程见 `../evidence/phase-reports/archive/`。

## 当前基线

| 基线 | 提交 | 状态 |
| --- | --- | --- |
| 项目实现 | `851fa5b` | 模块化单体与面试应用边界重构已合并 |
| 论文证据与正文同步 | `f064390` | 架构、报告链路、测试口径和正文已同步 |
| 论文交付治理 | `9ad17ad` | Word/WPS 人工终审边界已生效 |
| 正式证据冻结 | — | 当前证据可用，重新冻结仍需用户和审查官确认 |

## 当前有效证据

| 类别 | 路径 | 支撑内容 |
| --- | --- | --- |
| 证据矩阵 | `thesis-assets/evidence/test-data/test-evidence-matrix.md` | 第三至第五章的证据映射 |
| 功能测试 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 核心业务用例 |
| 质量门禁 | `thesis-assets/evidence/test-data/quality-gates-2026-07-13.md` | CI、本地测试和覆盖率门禁 |
| 环境与数据 | `thesis-assets/evidence/test-data/env-2026-06.md`、`thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md`、`thesis-assets/evidence/test-data/dev-fixture-2026-06.md` | 环境、数据库和本地验收数据 |
| 架构证据 | `thesis-assets/evidence/code-snippets/modular-monolith-boundary-hardening-2026-07-13.md` | 模块、分层与依赖方向 |
| 报告链路证据 | `thesis-assets/evidence/code-snippets/finish-job-async-report-2026-07-13.md` | `/finish` 到异步报告完成的当前链路 |
| 图表资产 | `thesis-assets/evidence/figure-table-register.md`、`thesis-assets/evidence/diagrams/` | 图表来源与准入状态 |
| 文献资产 | `thesis-assets/literature/` | 文献主库、质量复核、章节映射和最终编号 |
| 答辩资产 | `thesis-assets/defense/` | 讲稿与页级证据映射 |

## 当前项目事实

- 运行入口为 `start-dev.bat` 和 `start-docker.bat`；dev fixture 只用于 local/dev 验收。
- 后端是 Spring Boot 模块化单体，按业务模块以及 API、application、domain、infrastructure 职责组织。
- 面试 API 适配层负责 HTTP DTO 与 Command/Result/View 转换，面试 application 不导入本模块 API 包。
- `/finish` 经 `FinishInterview` 和 `JobSchedulerPort` 发布任务，由 RabbitMQ 适配器与 `ReportJobWorker` 完成异步报告链路。
- OpenAI-compatible BYOK 支持用户级 endpoint、API Key、模型发现和运行模型选择；API Key 加密保存。
- Redis 用于限流、缓存和状态辅助。
- CI 包含 whitespace、Sentrux、后端测试、JaCoCo、npm audit、前端构建以及 UI、token、BYOK、dark、a11y 门禁；视觉截图作为人工审查产物。

## 写作限制

- 模块化单体不得表述为微服务或可独立部署模块；application 层仍存在待收敛的框架与基础设施依赖。
- RabbitMQ 证据只覆盖本地链路与自动化测试，不支持生产级可靠投递、零丢失、DLQ、outbox 或 publisher confirm 结论。
- JaCoCo 70% instruction coverage 门禁只覆盖 `interview.application`、`resume.application`、`insight.application`，不代表全仓覆盖率。
- BYOK 自动化验证使用 mock API，不代表真实公网模型性能或任意 endpoint 兼容性。
- 语音证据只覆盖工程容错、顺序保护和 timeout，不支持真实 ASR/TTS 性能结论。
- seqNum、作业幂等和阶段系统消息测试不支持“并发绝对无冲突”的结论。
- `verify:ui` 与 `verify:tokens` 是静态规则门禁，不等同全量视觉回归。
- `verify:a11y` 只阻断 critical 问题，不等同完整 WCAG 2 AA 合规。
- `capture:visual` 只生成截图供人工审查，不是像素差异阻断测试。
