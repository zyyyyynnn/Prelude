# 图表与测试表登记

图表必须具备事实来源、章节位置和可复核文件。统一写作边界见 `../meta/final-evidence-lock.md`。

| 编号 | 名称 | 文件 | 章节 | 事实来源 | 状态 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 图3.1 | 系统核心用例图 | `diagrams/fig-3.1-core-use-case.png` | 第三章 | `diagrams/fig-3.1-core-use-case.mmd` | 已复核 | 待最终图号确认 |
| 图3.2 | 数据库 E-R 图 | `diagrams/fig-3.2-database-er.png` | 第三章 | `diagrams/fig-3.2-database-er.mmd`、`test-data/database-table-dictionary-2026-06.md` | 已复核 | 待最终图号确认 |
| 图3.3 | 系统整体架构图 | `diagrams/fig-3.3-system-architecture.png` | 第三章 | `diagrams/fig-3.3-system-architecture.mmd`、`code-snippets/modular-monolith-boundary-hardening-2026-07-13.md` | 已同步 | 待重新冻结 |
| 候选图4.x | SSE 流式问答处理流程图 | `diagrams/fig-4.x-sse-streaming-flow.png` | 第四章 | `diagrams/fig-4.x-sse-streaming-flow.mmd` | 候选 | 正文采用后确定图号 |
| 表5.1 | 测试环境 | `test-data/env-2026-06.md` | 第五章 | 环境采集记录 | 证据可用 | — |
| 表5.2 | 功能测试用例 | `test-data/functional-cases-2026-06.md` | 第五章 | TC-01 至 TC-12 | 证据可用 | — |
| 表5.3 | 简历解析与岗位匹配 | `test-data/functional-cases-2026-06.md` | 第五章 | PDF 与岗位匹配用例 | 证据可用 | — |
| 表5.4 | 模拟面试与 SSE 交互 | `test-data/functional-cases-2026-06.md`、`test-data/test-evidence-matrix.md` | 第五章 | 聊天、SSE、中止和恢复用例 | 证据可用 | — |
| 表5.5 | RabbitMQ 异步报告链路 | `test-data/functional-cases-2026-06.md`、`code-snippets/finish-job-async-report-2026-07-13.md` | 第五章 | 当前 finish/job 链路 | 证据可用 | — |
| 表5.6 | BYOK 用户级模型配置 | `test-data/functional-cases-2026-06.md`、`test-data/quality-gates-2026-07-13.md` | 第五章 | 设置与自动化验证 | 证据可用 | — |
| 表5.7 | Redis、限流与状态辅助 | `test-data/functional-cases-2026-06.md` | 第五章 | Redis 相关用例 | 证据可用 | — |
| 表5.8 | Structured Output 与报告解析 | `test-data/functional-cases-2026-06.md` | 第五章 | 结构化解析与边界用例 | 证据可用 | — |
| 表5.9 | 权限与数据隔离 | `test-data/functional-cases-2026-06.md`、`test-data/dev-fixture-2026-06.md` | 第五章 | JWT、跨用户访问和 fixture 边界 | 证据可用 | — |
| 补充证据 | 构建与自动化验证 | `test-data/quality-gates-2026-07-13.md` | 第五章 | CI 与本地门禁 | 证据可用 | 不单独占用正文表号 |
