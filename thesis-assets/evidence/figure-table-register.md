# 图表编号登记表

| 编号 | 类型 | 名称 | 文件路径 | 对应章节 | 来源证据 | 当前状态 | 是否可进入正文 | 风险说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 图3.1 | 图 | 系统核心用例图 | `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 第三章 | `fig-3.1-core-use-case.mmd` | 已复核 / 待正文图号冻结 | 是 | 无 |
| 图3.2 | 图 | 数据库 E-R 图 | `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | 第三章 | `fig-3.2-database-er.mmd` | 已复核 / 待正文图号冻结 | 是 | 无 |
| 图3.3 | 图 | 系统整体架构图 | `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 第三章 | `fig-3.3-system-architecture.mmd` | 已复核 / 待正文图号冻结 | 是 | 图 3.3 展示 MySQL、Redis、RabbitMQ 的当前职责。RabbitMQ 仅表示报告生成异步任务队列，不扩展为完整可靠消息处理方案。dev fixture 仅用于 local/dev 本地验收。 |
| 候选图4.x | 图 | SSE 流式问答处理流程图 | `thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.png` | 第四章 | `fig-4.x-sse-streaming-flow.mmd` | 候选 / 未冻结图号 | 待第四章采用确认 | 仅在正文实际引用时转为正式图号；不得提前冻结为图4.x |
| 表5.1 | 表 | 测试环境表 | `thesis-assets/evidence/test-data/env-2026-06.md` | 第五章 | 最新环境与依赖版本 | 已对齐 2026-06 证据 / 待正文采用确认 | 是 | 无 |
| 表5.2 | 表 | 功能测试用例表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 第五章 | TC-01 ~ TC-12 功能用例与本地验收边界 | 已对齐 2026-06 证据 / 待正文采用确认 | 是 | 语音能力仅可写容错与单元测试覆盖，真实 ASR/TTS 端到端延迟未实测 |
| 表5.3 | 表 | 简历解析与岗位匹配测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 第五章 | 简历上传、PDF 校验、文本提取、结构化字段和岗位匹配辅助用例 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | 不证明所有 PDF 类型或图片型简历均可准确解析 |
| 表5.4 | 表 | 模拟面试与 SSE 流式交互测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 第五章 | 聊天、SSE 分片、前端缓冲、请求中止和状态恢复证据 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | 不代表大规模长连接并发、丢包率或极端网络稳定性验证 |
| 表5.5 | 表 | RabbitMQ 异步报告生成链路测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/code-snippets/rabbitmq-report-queue-2026-06-13.md` | 第五章 | `/finish -> generating -> RabbitMQ -> ReportJobWorker -> summary_report -> finished -> report_ready` 闭环 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | RabbitMQ 仅表示报告生成异步任务队列，不证明生产级可靠投递、DLQ、outbox 或 publisher confirm |
| 表5.6 | 表 | BYOK 用户级模型配置测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | 第五章 | OpenAI-compatible endpoint、API Key、模型发现、配置保存、配置测试和链路复用 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | BYOK verify 是 mock API 与功能链路验证，不代表公网模型性能或兼容所有 endpoint |
| 表5.7 | 表 | Redis、限流与状态辅助测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 第五章 | Redis 连接、限流脚本、评分锁、缓存与状态辅助职责核对 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | 不代表大流量限流压测、Redis 故障降级或多节点一致性验证 |
| 表5.8 | 表 | Structured Output 与报告解析测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/code-snippets/structured-output-resilience-2026-06-02.md` | 第五章 | 结构化报告、反序列化、字段校验、分数范围处理和报告落库 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | 不保证模型输出始终合法或评分绝对客观 |
| 表5.9 | 表 | 权限与数据隔离测试表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/test-data/dev-fixture-2026-06.md` | 第五章 | JWT、跨用户资源访问、UserContext 清理和 dev fixture 边界 | 已对齐当前第五章表号 / 待正文采用确认 | 是 | dev fixture 仅用于 local/dev 本地验收，不进入 Full Docker / prod 默认路径 |
| 补充证据 | 表 | 构建与自动化验证记录 | `thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | 第五章 | 后端测试、前端构建、Sentrux、JaCoCo report-only、npm audit、BYOK verify、dark verify | active evidence / 未作为当前正文编号表 | 是 | JaCoCo 仅 report-only，无覆盖率阈值；Sentrux 规则有限，不等同全量架构证明 |
