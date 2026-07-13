# 答辩 PPT 页级映射表

## 使用口径

本表用于把答辩 PPT 每一页与论文正文、截图、代码证据和测试数据绑定。当前为 14 页建议版，可直接作为 PPT 制作大纲。

当前演示口径：`start-dev.bat` + dev fixture 本地验收数据；旧 Demo Twin 只作为历史复盘材料，不作为当前展示路线。

| PPT 页码 | 页面主题 | 使用截图/代码/数据 | 对应论文章节 | 现场讲解要点 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 第 1 页 | 题目与个人信息 | 项目名称、系统名称 | 封面 | 说明课题是“基于 LLM 的模拟面试与简历诊断系统”。 | 按学校模板填写姓名、专业、导师 |
| 第 2 页 | 选题背景与问题 | 文献要点、求职训练场景 | 第一章 1.1-1.2 | 说明求职者需要低成本、可重复、可反馈的面试训练工具。 | 不堆长文献 |
| 第 3 页 | 系统目标与业务闭环 | `docs/images/interview-empty.png` 或当前工作台截图 | 第一章 1.3、第三章 3.1 | 强调系统覆盖简历、岗位、面试、报告、回放和分析。 | 可接展示路线 |
| 第 4 页 | 总体架构 | `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 第三章 3.4 | 讲清前端 feature、后端模块化单体、应用端口、平台能力与基础设施适配关系。 | 模块不可独立部署；RabbitMQ 只表示报告生成异步任务队列 |
| 第 5 页 | 核心用例 | `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 第三章 3.1 | 说明用户从登录到分析的主流程，以及配置和回放的辅助能力。 | 图3.1 |
| 第 6 页 | 数据库设计 | `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | 第三章 3.3 | 重点讲用户、简历、会话、消息、阶段、评分和薄弱点之间的关系。 | 图3.2 |
| 第 7 页 | 核心实现一：SSE 流式面试 | `fig-4.x-sse-streaming-flow.png`、SSE 代码片段 | 第四章 | 说明后端 SseEmitter 流式推送、前端 rAF 缓冲渲染和结束收口。 | 候选图4.x，正文采用前再冻结图号 |
| 第 8 页 | 核心实现二：Structured Output | JSON Schema / 解析降级代码片段 | 第四章 | 说明旧正则评分风险，当前使用结构化输出、反序列化和分数降级。 | 对比新旧方案 |
| 第 9 页 | 核心实现三：可靠性保护 | Redis 限流、Resilience4j、fallback 测试 | 第四章 | 说明限流、熔断、SSE 恢复，以及 openai-compatible 不静默 fallback 的边界。 | 限缩为机制与测试保护，不写高并发通过 |
| 第 10 页 | 测试验证 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md`、`thesis-assets/evidence/test-data/quality-gates-2026-07-13.md`、`thesis-assets/evidence/test-data/test-evidence-matrix.md` | 第五章 | 展示 TC-01 至 TC-12、后端测试、前端构建、Sentrux、覆盖率与 UI 自动化门禁。 | 结论边界见 `thesis-assets/meta/final-evidence-lock.md` |
| 第 11 页 | Bug 复盘 | `thesis-assets/evidence/bug-evidence/archive/01-demo-proxy.md`、`thesis-assets/evidence/bug-evidence/archive/02-mysql-preflight.md` | 第四章、第五章 | 讲历史代理错连和 MySQL 未就绪两个真实问题，突出配置契约和启动前置校验。 | 只讲 1-2 个重点 |
| 第 12 页 | 系统展示路线 | `start-dev.bat`、当前 README / setup | 全文 | 按登录、主工作台、简历、面试、报告、看板、LLM 配置、设置展示。 | 准备离线截图兜底 |
| 第 13 页 | 总结与不足 | 第六章总结、局限与改进方向 | 第六章 | 总结闭环、SSE、Structured Output、可靠性保护、Docker 编排和证据化测试；说明语音和并发压测不足。 | 收束到可扩展方向 |
| 第 14 页 | Q&A 准备 | `thesis-assets/defense/script.md`、`thesis-assets/meta/final-evidence-lock.md`、`thesis-assets/evidence/README.md` | — | 准备 SSE、fallback、RabbitMQ、BYOK、语音与质量门禁边界等问答。 | 可选页 |

## 关键提示

- 当前测试数据代表 dev fixture 本机闭环、CI 自动化验证和一次 BYOK 功能链路补充，不代表真实公网 LLM 响应性能。
- 语音交互只能写成工程容错、顺序保护和待实测能力，不得写成真实公网低延迟已完成。
- fig-4.x 为候选图号，仅在正文实际引用时转为正式图号。
- 每页讲解控制在 30 到 45 秒，现场展示页可适当延长。
- RabbitMQ / JaCoCo 70% / `verify:ui` / `verify:a11y` / `capture:visual` 等写作限制见 `meta/final-evidence-lock.md` 写作限制段，本表不复述。
