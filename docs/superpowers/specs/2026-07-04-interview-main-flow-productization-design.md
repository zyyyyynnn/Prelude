# 沉浸式模拟面试主链路产品化设计

## 目标

将现有“选择简历与岗位后直接聊天”的流程扩展为：阶段化面试、后台评分、结构化训练报告和能力画像沉淀。保留现有 BYOK、SSE、RabbitMQ、阶段状态机、语音链路与旧 Markdown 报告兼容。

## 范围边界

- 不修改 `[STAGE_COMPLETE]` 驱动的阶段推进机制。
- 不新增数据库表或报告专用短板数据源。
- 不引入外部依赖，不引入 MCP。
- 面试过程继续保存 `score`/`hint`，但前端不展示实时评分或建议。

## 后端设计

### 结构化报告模型

最终 `StructuredInterviewReport` 包含：

- `summary`：岗位适配判断、行动建议、总体风险。
- `scores`：technical、expression、logic 和由前三项均值计算的 overall。
- `stagePerformances`：阶段、叙述总结、正/负信号、改进建议，以及由该阶段用户消息已落库分数计算的 score。
- `questionReviews`：问题、回答摘要、已落库 score/hint 和叙述性改进建议。
- `strengths`。
- `weaknesses`：从同一 session 的 `UserWeakness` 行格式化生成。
- `trainingPlan`：3 天、7 天和下次模拟重点。
- `finalAdvice`。
- `markdownFallback`。

`InterviewReportParser` 只解析 LLM 或 fixture 提供的叙述草稿。输入 schema 不包含 `scores.overall`、`stagePerformances.score`、`questionReviews.score/scoringReason` 或 `weaknesses`，从类型层面避免第二套评分和短板来源。

`InterviewReportAssembler` 是真实报告与 dev fixture 的唯一最终合并入口。它按阶段时间窗口关联消息，以 assistant→下一条 user 的顺序构建逐题复盘，直接读取 user 消息的 `score` 和 `hint`；题目缺失、评分缺失或语音字段不完整时生成可理解 fallback，不使任务失败。阶段均分仅统计对应阶段非空的已落库分数。

### RabbitMQ 报告任务

`ReportJobWorker` 保持现有消费、状态恢复、ScoreHistory、UserWeakness 提取与 `report_ready` 事件。处理顺序调整为：

1. 获取消息与阶段。
2. 获取 LLM/fixture 叙述草稿并解析。
3. 保持现有薄弱点提取并落库。
4. 调用共享 assembler 生成最终 JSON。
5. 保存 `summary_report` 并广播同一 JSON。

非法 LLM 输出由 parser 生成完整 fallback 草稿；旧数据库中的纯 Markdown 不重写，前端继续 fallback 渲染。

### dev fixture

`DevFixtureCatalog.report()` 的三个岗位改为可解析的叙述 JSON，保留当前 Markdown 评分、优势、建议和结论文本。JSON 不提供阶段分数、逐题分数、overall 或 weaknesses。

`DevFixtureService.createFinishedSession()`：

- 为每条 user 消息调用现有 `resolveMockJudge(stageName, stageQuestionIndex)`，将 score/hint 写在 user 行。
- 按每个 `QnaPair.stageName()` 对应阶段窗口计算时间戳。
- 先插入既有 ScoreHistory 与 UserWeakness，再调用共享 assembler 生成最终 `summary_report`。
- 不硬编码最终 `StructuredInterviewReport`。

升级后通过现有 `/api/dev-fixtures/reset` 全量重建 demo 用户数据。

## 前端设计

### 面试过程

从 `MessageThread.vue` 删除 score pill、hint tooltip、相关 import、transition 和样式。语音与文字共用 messages，因此两条链路同时生效；后端 judge 与消息字段保持不变。

### 结构化报告

`parseInterviewReport` 先尝试解析结构化 JSON并归一化缺失字段；失败时返回 Markdown fallback 模式。结构化面板拆为总览、三维评分、阶段表现、逐题复盘、优势/短板和训练计划组件。所有集合使用真实 `ul`/`ol`。

报告根节点仍由 `InterviewView.vue` 的 `reportRef` 包裹，PDF 导出继续对完整 DOM 截图。`pdf.ts` 的不可分页选择器扩展到新面板条目，避免空白或主要卡片被截断。

## 设计系统

`DESIGN.md` 6.3 删除实时评分规范；6.5 增加结构化报告的布局、状态和响应式约束。新增 UI 只使用现有语义 token、弱边界和低阴影，不修改 shared-dropdown，不增加内联颜色。

## 测试与验收

- 后端按 TDD 覆盖非法 JSON、分数钳制、共享 assembler 评分来源、阶段均分、question 数量补齐、weakness 格式化、fixture 合并及 RabbitMQ 成功/失败。
- Playwright mock API 增加结构化报告，覆盖桌面和小屏、不显示实时评分、旧 Markdown fallback、PDF 导出非空。
- 运行后端测试/打包、前端 build、UI/tokens/a11y/visual 门禁并更新预期视觉资产。
- local/dev 启动后调用 fixture reset，登录 demo 检查历史报告和新报告；验证文字报告、语音数据兼容和 PDF 导出。

## 兼容与失败策略

- LLM 非 JSON、字段缺失、越界分数：parser/assembler 归一化并保留 fallback 文案。
- 旧 Markdown 报告：继续 Markdown 渲染。
- 报告任务失败：保留现有 error SSE 与状态恢复。
- 语音消息缺字段：逐题复盘以“暂无评分/建议”兼容，但新 demo fixture 必须补齐真实 mock judge 分数。
