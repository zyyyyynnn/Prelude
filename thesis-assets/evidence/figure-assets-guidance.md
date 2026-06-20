# 图表资产准入说明

## 文件定位

本文件约束论文图表、界面截图和数据可视化资产的准入条件。它不是正文，不冻结图号，不替代测试证据。

图表只能表达已有代码事实、已有测试证据或已登记截图事实。

## 图表资产分层

| 资产类型 | 主要工具 | 适用内容 | 当前状态 | 禁止事项 |
| --- | --- | --- | --- | --- |
| Mermaid / `.mmd` | Mermaid CLI | 用例图、E-R 图、系统架构图、流程图 | 主事实图源 | 禁止加入源码中不存在的模块、组件或链路 |
| draw.io / 人工排版 | draw.io | 对 Mermaid 导出图进行版面优化 | 可选人工微调 | 不得改变事实节点、层次结构和依赖关系 |
| nature-figure | Python Matplotlib / Seaborn | 对已有真实测试数据做出版级可视化 | 可选增强工具 | 不得生成未实测性能图或科研式伪图 |
| 界面截图 | 浏览器截图 / Playwright | 展示真实页面、入口和流程可达性 | 待按需刷新登记 | 禁止使用旧版本截图或 AI 补造界面 |
| 测试表格 | Markdown 表格 | 第五章功能、环境、质量门禁和边界测试 | 优先呈现方式 | 禁止把未实测项写成完全通过 |

## 当前准入图表清单

| 编号 | 图表名称 | 当前资产 | 事实来源 | 准入状态 |
| --- | --- | --- | --- | --- |
| 图3.1 | 系统核心用例图 | `evidence/diagrams/fig-3.1-core-use-case.png` | `fig-3.1-core-use-case.mmd` | 已复核 / 待正文图号冻结 |
| 图3.2 | 数据库 E-R 图 | `evidence/diagrams/fig-3.2-database-er.png` | `fig-3.2-database-er.mmd`、`database-table-dictionary-2026-06.md` | 已复核 / 待正文图号冻结 |
| 图3.3 | 系统整体架构图 | `evidence/diagrams/fig-3.3-system-architecture.png` | `fig-3.3-system-architecture.mmd` | 已复核 / 待正文图号冻结 |
| 候选图4.x | SSE 流式问答处理流程图 | `evidence/diagrams/fig-4.x-sse-streaming-flow.png` | `fig-4.x-sse-streaming-flow.mmd` | 候选 / 未冻结图号 |
| 表5.1 | 测试环境表 | `evidence/test-data/env-2026-06.md` | 本地环境采集记录 | 可进入正文候选 |
| 表5.2 | 功能测试用例表 | `evidence/test-data/functional-cases-2026-06.md` | TC-01 ~ TC-12 | 可进入正文候选 |
| 表5.3 | 简历解析与岗位匹配测试表 | `evidence/test-data/functional-cases-2026-06.md` | PDF 校验、文本提取、结构化字段、岗位匹配 | 可进入正文候选 |
| 表5.4 | 模拟面试与 SSE 流式交互测试表 | `functional-cases-2026-06.md`、`test-evidence-matrix-2026-06.md` | SSE 分片、前端缓冲、请求中止、状态恢复 | 限制性可写 |
| 表5.5 | RabbitMQ 异步报告生成链路测试表 | `functional-cases-2026-06.md`、`code-snippets/rabbitmq-report-queue-2026-06-13.md` | 报告生成异步任务队列闭环 | 限制性可写 |
| 表5.6 | BYOK 用户级模型配置测试表 | `functional-cases-2026-06.md`、`quality-gates-2026-06-19.md` | 模型发现、配置保存、配置测试、链路复用 | 限制性可写 |
| 表5.7 | Redis、限流与状态辅助测试表 | `functional-cases-2026-06.md` | 限流、缓存、评分锁、状态辅助 | 限制性可写 |
| 表5.8 | Structured Output 与报告解析测试表 | `functional-cases-2026-06.md`、`code-snippets/structured-output-resilience-2026-06-02.md` | 结构化报告、字段校验、分数边界处理 | 限制性可写 |
| 表5.9 | 权限与数据隔离测试表 | `functional-cases-2026-06.md`、`dev-fixture-2026-06.md` | JWT、跨用户资源访问、dev fixture 边界 | 可进入正文候选 |
| 补充证据 | 构建与自动化验证记录 | `quality-gates-2026-06-19.md` | 构建、CI、质量门禁 | active evidence / 未作为当前正文编号表 |

## UI 截图候选

| 代号 | 截图 | 用途 | 状态 |
| --- | --- | --- | --- |
| UI-01 | `docs/images/login.png` | 登录页展示 | 候选，使用前复核 |
| UI-02 | `docs/images/register.png` | 注册页展示 | 候选，使用前复核 |
| UI-03 | `docs/images/interview-empty.png` | 工作台空状态 | 候选，使用前复核 |
| UI-04 | `docs/images/resumes.png` | 简历管理 | 候选，使用前复核 |
| UI-05 | `docs/images/interview-chat.png` | 面试对话 | 候选，使用前复核 |
| UI-06 | `docs/images/interview-report.png` | 面试报告 | 候选，使用前复核 |
| UI-07 | `docs/images/analytics.png` | 数据分析 | 候选，使用前复核 |

UI 截图在进入正文或答辩正式稿前，需确认来自当前真实运行系统，并登记到图表索引。

## 禁止可视化内容

| 禁止内容 | 原因 |
| --- | --- |
| 高并发压测结果图 | 无对应压测数据日志 |
| 限流触发率曲线 | Redis 限流有代码机制，但无高流量统计 |
| 熔断切换效果曲线 | 无接口雪崩或熔断时延曲线数据 |
| SSE 长连接并发稳定性图 | 无并发长连接断线和丢包率监测日志 |
| ASR 语音识别准确率图 | 无真实公网 ASR 大样本统计 |
| AI 评分公平无偏图 | 无盲测、偏差和公平性量化实验 |
| 招聘效果优于人工图 | 无真实招聘转化或 HR 录用统计 |
| 生产环境性能图 | 有效数据来自本地或单次功能链路，不是生产部署 |

## 图表进入论文的准入条件

1. 有真实数据源、代码事实或已登记截图事实。
2. 有明确章节承载位置。
3. 已在 `figure-table-register.md` 登记。
4. 风险说明完整，不把本地或单次测试包装成生产指标。
5. 经用户和审查官复核。
6. 正文排版前不冻结最终物理图号。
