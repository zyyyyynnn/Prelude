# 图表编号登记表

| 编号 | 类型 | 名称 | 文件路径 | 对应章节 | 来源证据 | 当前状态 | 是否可进入正文 | 风险说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 图3.1 | 图 | 系统核心用例图 | `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 第三章 | `fig-3.1-core-use-case.mmd` | 已复核 / 待正文图号冻结 | 是 | 无 |
| 图3.2 | 图 | 数据库 E-R 图 | `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | 第三章 | `fig-3.2-database-er.mmd` | 已复核 / 待正文图号冻结 | 是 | 无 |
| 图3.3 | 图 | 系统整体架构图 | `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 第三章 | `fig-3.3-system-architecture.mmd` | 已复核 / 待正文图号冻结 | 是 | 图 3.3 展示 MySQL、Redis、RabbitMQ 的当前职责。RabbitMQ 仅表示报告生成异步任务队列，不扩展为完整可靠消息处理方案。dev fixture 仅用于 local/dev 本地验收。 |
| 候选图4.x | 图 | SSE 流式问答处理流程图 | `thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.png` | 第四章 | `fig-4.x-sse-streaming-flow.mmd` | 候选 / 未冻结图号 | 待第四章采用确认 | 仅在正文实际引用时转为正式图号；不得提前冻结为图4.x |
| 表5.1 | 表 | 测试环境表 | `thesis-assets/evidence/test-data/env-2026-06.md` | 第五章 | 最新环境与依赖版本 | 已对齐 2026-06 证据 / 待正文采用确认 | 是 | 无 |
| 表5.2 | 表 | 功能测试用例表 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 第五章 | 指向最新功能用例；2026-06-19 已补齐质量门禁、BYOK、TTS、fallback、seqNum 与报告任务幂等口径 | 已对齐 2026-06-19 证据 / 待正文采用确认 | 是 | 语音能力仅可写容错与单元测试覆盖，真实 ASR/TTS 端到端延迟未实测 |
| 表5.3 | 表 | 构建与自动化验证记录表 | `thesis-assets/evidence/test-data/env-2026-06.md`、`thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | 第五章 | 后端测试、前端构建、Sentrux、JaCoCo report-only、npm audit、BYOK verify、dark verify | 已补充 2026-06-19 质量门禁证据 / 待正文采用确认 | 是 | JaCoCo 仅 report-only，无覆盖率阈值；Sentrux 规则有限，不等同全量架构证明 |
| 表5.4 | 表 | 业务性能采集记录表 | `thesis-assets/evidence/test-data/dev-fixture-2026-06.md` | 第五章 | dev fixture 本地验收数据隔离验证 | 待补充当前阶段实测证据 | 是 | 多数真实性能指标未实测，禁止写成公网或高并发性能结论 |
