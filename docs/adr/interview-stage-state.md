# Interview 显式阶段状态

## Context

面试同时存在会话生命周期、阶段顺序、助手提问是否待答、回答评估与报告生成状态。若从消息时间戳或分散布尔值推断阶段，会产生边界歧义、重复推进和非法并发转换。

## Decision

- `InterviewSessionStatus` 显式允许 `ongoing → generating → finished`，以及失败/取消后的 `generating → ongoing`；其他转换直接拒绝。
- `InterviewStageDefinition` 是阶段目录唯一真源，定义 `warmup/technical/deep_dive/closing` 的顺序与回答配额 1/2/2/1。
- `interview_stage` 持久化 `boundary_seq` 与 `awaiting_answer`；阶段边界不再从时间戳猜测。
- 用户消息清除待答，助手消息设置待答；手动推进在待答时拒绝，自动推进只统计边界序号后的助手回答。
- 消息序号由会话行锁与唯一键保证；助手消息、待答状态和阶段推进在同一事务提交。
- 回答评估使用 `PENDING/SCORED/UNSCORED`；仅 `closing` 且无 PENDING 时可领取报告生成。
- Job attempt fence、报告与 Analytics 同事务提交；实时事件不承载状态真源。

## Consequences

状态转换可由数据库行直接解释，重试与并发收尾具备确定性。阶段新增需要同时更新目录、配额、提示词、前端类型与测试。持久字段增加后必须通过 Flyway 增量迁移，不得修改已应用版本。

## Validation

`InterviewSessionStatusTest` 验证合法转换；`InterviewStageCatalogTest`、`InterviewStagePolicyTest`、`InterviewStageManagerTest`、`UpdateInterviewStageTest` 验证阶段目录与推进；`InterviewTurnCompletionTest` 验证同事务回滚；`InterviewMessageOrderMySqlTest`、`InterviewMessageStatusGateTest` 与 `FinishInterviewStatusRaceTest` 验证顺序和生命周期门禁；`InterviewJudgeMySqlTest` 验证评估持久化。
