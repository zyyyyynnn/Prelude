# 当前论文证据锁定索引

## 文件定位

本文件只登记当前仍有效、可追溯、可进入后续阶段审查的论文证据资产。它不是阶段过程报告，也不承载长篇实现说明。若与其他资产说明冲突，以 `workflow-governance.md` 为流程最高规范，以本文件列出的 active evidence 为事实入口。

## 当前有效证据资产

| 证据类型 | 当前路径 | 用途 | 当前状态 | 限制说明 |
| --- | --- | --- | --- | --- |
| 参考文献主库 | `thesis-assets/literature/references.bib` | 参考文献数据库 | 已收口 | 由 Zotero / Better BibTeX 管理 |
| 文献质量复核表 | `thesis-assets/literature/quality-review.md` | 文献质量审查依据 | 已收口 | 不冻结正文引用编号 |
| 文献证据映射表 | `thesis-assets/literature/evidence-map.md` | 文献到章节的证据映射 | 已收口 | 不等同正文引用顺序 |
| 最终参考文献编号锁定 | `thesis-assets/literature/final-reference-lock.md` | DOCX 整合前参考文献编号与正文引用闭环 | 待人工终审 / 可用 | 不等同 references.bib 全量候选 |
| 图表登记表 | `thesis-assets/evidence/figure-table-register.md` | 图表资产索引 | 已更新 | 图表进入正文前必须登记 |
| 绘图与模型资产 | `thesis-assets/evidence/diagrams/` | 图表源文件与导出图 | 可用 | 候选图 4.x 未冻结图号 |
| 测试证据矩阵 | `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 测试数据与章节可写性映射 | 可用 | 当前主口径为 start-dev / start-docker / dev fixture；旧 Demo 数据仅作历史对照 |
| 功能用例 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 表 5.2 功能测试用例 | 可用 | 语音、压测和公网性能必须限制性描述 |
| 环境与构建记录 | `thesis-assets/evidence/test-data/env-2026-06.md` | 表 5.1 测试环境与第五章构建记录补充证据 | 可用 | 代表本地阶段性采集，不等同生产环境 |
| 质量门禁证据 | `thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | CI / 本地质量门禁与小重构验证证据 | 可用 | JaCoCo report-only；BYOK/dark verify 为自动化流程验证 |
| 数据库表字典 | `thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md` | 数据库表结构参考 | 可用 | 补充 E-R 图字段细节 |
| 代码片段证据 | `thesis-assets/evidence/code-snippets/` | 系统实现依据 | 可用 | 旧正则评分证据已被 Structured Output 证据替代 |
| Bug 与修复证据 | `thesis-assets/evidence/bug-evidence/` | 精选问题复盘与答辩依据 | 可用 | 仅保留可直接引用证据，不夸大为系统能力证明 |
| 阶段过程记录 | `thesis-assets/evidence/phase-reports/` | 审计和追溯 | 降权保留 | 不直接作为正文事实依据 |
| 答辩材料 | `thesis-assets/defense/` | PPT 映射与讲稿 | 可用 | 使用前仍需人工核对最新口径 |
| 阶段 3 准备冻结报告 | `thesis-assets/evidence/phase-reports/phase-3-readiness-freeze-2026-06-20.md` | Final Evidence Freeze 与答辩准备核对 | 当前冻结入口 | 只冻结证据口径，不代表正文已修改 |

## 当前锁定边界

- 当前证据索引只确认资产位置与用途，不代表正文已经完成同步。
- 当前冻结基线为 `4b2e967`；阶段 3 准备工作只完成 evidence、图表登记和答辩材料口径核对。
- 正文未修改，引用编号未冻结，DOCX/PDF 未生成。
- 新增证据必须先进入 active evidence 路径，再由用户和审查官复核。
- 不允许通过临时输出或外部生成物反向覆盖 `chapters/*.md`。

## 项目事实口径

- 当前运行入口：`start-dev.bat` 和 `start-docker.bat`。旧 Demo Twin、`start-demo`、`start-real`、8081/5174 仅属于历史归档或过程材料。
- 报告生成：RabbitMQ 已承担报告异步任务队列；`/finish` 将 session 置为 `generating` 并发布 `ReportJobMessage`，`ReportJobWorker` 消费后通过 SSE 推送 `report_ready`。
- BYOK：OpenAI-compatible 支持用户级 endpoint、API Key、模型发现与运行模型选择；API Key 加密保存，具体运行模型不作为默认配置或论文推荐依据。
- Redis：承担限流、缓存和状态辅助职责。
- 质量门禁：CI 包含 whitespace diff check、Sentrux、后端测试、JaCoCo report artifact、npm audit、前端 build、BYOK verify 和 dark verify。

## 写作限制

- RabbitMQ 只能写成本地 Docker Compose 与单元测试支撑的异步报告链路，不得写成生产级可靠投递或消息零丢失。
- JaCoCo 只能写成 coverage report artifact，不得写成覆盖率阈值达标。
- BYOK verify 是 mock API 浏览器自动化流程验证，不得写成真实公网模型性能验证。
- TTS 单元测试覆盖容错、顺序和 timeout，不得写成真实 ASR/TTS 低延迟性能基准。
- openai-compatible 失败必须显式暴露，不写成无感 fallback 到系统 provider。
- seqNum、ReportJobWorker 幂等和阶段系统消息测试可作为一致性证据，不得写成并发绝对无冲突。
