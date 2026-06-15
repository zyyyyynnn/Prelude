# 答辩 PPT 页级映射表

## 使用口径

本表用于把答辩 PPT 每一页与论文正文、截图、代码证据和测试数据绑定。当前为 14 页建议版，可直接作为 PPT 制作大纲。

| PPT 页码 | 页面主题 | 使用截图/代码/数据 | 对应论文章节 | 现场讲解要点 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 第 1 页 | 题目与个人信息 | 项目名称、系统名称 | 封面 | 简要说明课题是"基于 LLM 的模拟面试与简历诊断系统"。 | 按学校模板填写姓名、专业、导师 |
| 第 2 页 | 选题背景与问题 | 文献要点、就业面试训练场景 | 第一章 1.1-1.2 | 说明求职者需要低成本、可重复、可反馈的面试训练工具。 | 不堆长文献 |
| 第 3 页 | 系统目标与业务闭环 | `docs/images/interview-workbench.png` | 第一章 1.3、第三章 3.1 | 强调系统覆盖简历、岗位、面试、报告、回放和分析，不是单点页面。 | 可接展示路线 |
| 第 4 页 | 总体架构 | `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 第三章 3.4 | 讲清前端、后端、MySQL、Redis、RabbitMQ、LLM Provider 和 dev fixture 的关系，其中 RabbitMQ 仅表示报告生成异步任务队列。 | 图3.3 |
| 第 5 页 | 核心用例 | `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 第三章 3.1 | 说明用户从登录到分析的主流程，以及配置和回放的辅助能力。 | 图3.1 |
| 第 6 页 | 数据库设计 | `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | 第三章 3.3 | 重点讲用户、简历、会话、消息、阶段、评分和薄弱点之间的关系。 | 图3.2 |
| 第 7 页 | 核心实现一：SSE 流式面试 | `thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.png`、SSE 代码片段 | 第四章 | 说明后端 SseEmitter 流式推送、前端 rAF 缓冲渲染和 `[DONE]` 收口。 | 候选图4.x |
| 第 8 页 | 核心实现二：Structured Output | JSON Schema 代码片段、反序列化降级逻辑 | 第四章 | 说明旧版正则评分的风险，新版 Jackson 反序列化 + clampScore + 安全降级 6 分。 | 对比新旧方案 |
| 第 9 页 | 核心实现三：高可用机制 | Redis 限流脚本、Resilience4j 配置、SSE 重连逻辑 | 第四章 | 说明 Redis Lua 令牌桶限流、熔断器自动切换备用 Provider、SSE 指数退避重连。 | 限缩为机制说明，不写成已验证的高并发能力 |
| 第 10 页 | 测试验证 | `thesis-assets/evidence/test-data/env-2026-06.md`、`functional-cases-2026-06.md`、`dev-fixture-2026-06.md` | 第五章 | 展示 TC-01 到 TC-09 全通过，后端 14 个单元测试全绿，前端构建通过。明确 dev fixture 本机回环口径。 | 旧 4 月数据仅作 archive 历史对照 |
| 第 11 页 | Bug 复盘 | `thesis-assets/evidence/bug-evidence/bug-fix-cases-2026-04-24.md` | 第四章、第五章 | 讲历史 Demo 代理错连问题和 MySQL 未就绪两个真实问题，突出排查和脚本前置校验。 | 选 1-2 个重点讲 |
| 第 12 页 | 系统展示路线 | `thesis-assets/defense/defense-dev-runbook-2026-06.md` | 全文 | 按登录、主工作台、简历、面试、报告、看板、LLM 配置、设置展示。 | 准备离线截图兜底 |
| 第 13 页 | 总结与不足 | 第六章总结、局限与改进方向 | 第六章 | 总结完整闭环、SSE 体验、Structured Output、高可用机制、Docker 编排和证据化测试；说明语音交互和部分性能指标仍为待实测状态。 | 收束到可扩展方向 |
| 第 14 页 | Q&A 准备 | `thesis-assets/defense/defense-dev-runbook-2026-06.md` 第五节 | — | 预备 6-8 个高频追问的口径（SSE vs WS、熔断降级、RabbitMQ 现状、语音待实测、测试口径等）。 | 可选页，视答辩时间决定是否展示 |

## 关键提示

- 当前测试数据代表 dev fixture 本机闭环与用户级 BYOK 链路补充验证，不代表真实公网 LLM 响应性能。
- 语音交互（Voice/WebSocket）只写成规划或待实测能力，不得写成真实公网低延迟已完成。
- RabbitMQ 已用于报告生成异步链路并完成本地闭环验证（`/finish → RabbitMQ → @RabbitListener → report_ready`）；Redis 回归限流、缓存与状态辅助职责。本次验证为本地基础链路联调，不等同于公网高并发压测，不证明生产级可靠投递或消息绝不丢失。
- fig-4.x 为候选图号，仅在正文实际引用时转为正式图号。
- 若后续制作正式 PPT，应优先使用图 3.1 至图 3.3、候选图 4.x、主工作台截图、报告截图和两条 Bug 复盘。
- 每页讲解控制在 30 到 45 秒，现场展示页可适当延长。
