# 模拟面试主链路

## 产品定位

Prelude 的模拟面试主链路是一套真实求职训练流程：用户以自己的简历、目标岗位和可选 JD 为输入，直接进入阶段化追问，结束后通过结构化报告复盘并把薄弱点沉淀到能力画像。

它不是岗位推荐或招聘信息聚合系统，不提供岗位 TopN、薪资预测、地区热度或伪精确匹配百分比。

## 用户主链路

1. 选择本人简历和目标岗位，可选粘贴 JD。
2. 开始文字或语音模拟面试。
3. 按 `warmup`、`technical`、`deep_dive`、`closing` 阶段完成追问。
4. 后端对每条候选人回答异步评分并保存，不在面试过程中展示分数。
5. 结束面试后由 RabbitMQ 报告任务生成结构化训练报告。
6. 报告中的薄弱点进入 `user_weakness`，供 Analytics 按类别聚合。

## 阶段化面试

阶段推进继续由现有 `InterviewStageManager` 和 `[STAGE_COMPLETE]` 标签驱动。本阶段不增加强制题数、阶段阻塞、强制跳转或自动结束规则。

文字链路使用 REST + SSE；语音链路使用 WebSocket，并复用相同的会话、消息、阶段、评分和报告数据模型。BYOK 与会话级 LLM provider/model 快照保持不变。

## 后台评分策略

`InterviewJudgeService` 在面试过程中评分，并把 `score` 与 `hint` 写回候选人回答对应的 `interview_message` user 行。

前端 `MessageThread` 不展示实时数字评分或 hint。所有分数、评分依据和改进建议统一在面试结束后的报告中展示，避免即时分数干扰沉浸式回答。文字与语音消息共用该组件，因此展示策略一致。

## 结构化训练报告

`summary_report` 继续使用现有 TEXT 字段，但新报告保存为结构化 JSON；旧纯 Markdown 数据继续走 fallback。

主要字段：

- `summary`：岗位适配判断、行动建议、总体风险。
- `scores`：技术能力、表达清晰度、逻辑思维和总体分。
- `stagePerformances`：四阶段总结、阶段均分、正向/风险信号和改进建议。
- `questionReviews`：问题、回答摘要、得分、评分依据和改进建议。
- `strengths`：核心优势。
- `weaknesses`：由当前 session 的 `UserWeakness` 行格式化生成。
- `trainingPlan`：3 天补强、7 天专项和下次模拟重点。
- `finalAdvice`：总结建议。
- `markdownFallback`：旧展示与导出兼容文本。

逐题 `score` 和 `scoringReason` 只读取已落库的 user 消息 `score/hint`。阶段分数只计算该阶段已落库逐题分数均值。总体分只计算三维分数均值。报告生成 LLM 和 dev fixture 草稿都不能提供这些派生字段，避免同一回答出现两套分数。

## 能力画像沉淀

报告生成沿用现有薄弱点提取与 `user_weakness` 落库流程。结构化报告 assembler 从这些已落库行生成“主要短板”，不建立报告页私有短板数据源；Analytics 继续按 category 聚合同一批数据。三维分数继续写入 `score_history`。

## Service 边界

- `InterviewStageManager`：阶段状态与现有推进规则。
- `InterviewJudgeService`：逐题评分并写回 user 消息。
- `InterviewReportParser`：只解析和归一化 LLM/fixture 的叙述草稿。
- `InterviewReportAssembler`：从草稿、消息、阶段和 weaknesses 生成唯一最终报告。
- `ReportJobWorker`：RabbitMQ 消费、报告草稿获取、能力数据持久化、最终报告保存与 SSE 通知。
- `DevFixtureService`：生成可复现数据，但复用相同 parser/assembler，不硬编码最终报告。

这些内部能力是普通 Spring service 边界。本阶段不引入 MCP，因为主链路不需要外部工具发现、远程资源协议或新的运行基础设施；增加 MCP 会扩大部署和安全面，不能直接改善当前训练闭环。

## dev fixture 同步约束

dev fixture 必须代表当前产品 schema：

- 三个岗位报告源均为结构化叙述 JSON，保留对应 Markdown fallback。
- 每条 user 消息通过现有 `resolveMockJudge` 写入 score/hint。
- 消息时间必须落入其 Q&A 所属阶段窗口。
- 历史 finished session 与新生成报告共用 `InterviewReportAssembler`。
- schema 或报告字段变化后，必须同步 fixture，并通过 `/api/dev-fixtures/reset` 重建 `demo / 123456` 数据后验收。

Full Docker / prod 默认不启用 dev fixture。
