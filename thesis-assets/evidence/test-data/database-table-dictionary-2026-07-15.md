# 数据库表结构证据（2026-07-15）

事实源：`backend/src/main/resources/schema.sql`。图3.2只保留核心关系，本文件登记当前 12 张表的字段职责。

| 表名 | 主要用途 | 关键关系 | 关键字段 |
| --- | --- | --- | --- |
| `user` | 账号、资料、主题与用户级 LLM 配置 | 主键 `id` | `username`、BCrypt `password`、`llm_provider`、`llm_model`、`llm_base_url`、`llm_api_key_encrypted` |
| `resume` | PDF/编辑器简历与结构化真源 | `user_id -> user.id` | `raw_text`、`document_json`、`document_version`、`source_type`、`plain_text_projection` |
| `position_template` | 岗位目录与系统提示词 | 名称唯一 | `name`、`system_prompt` |
| `interview_session` | 一次面试的模型快照和状态机 | 指向 `user`、`resume`、`position_template` | `status` 为 `ongoing/generating/finished`，以及 `summary_report`、`jd_text` |
| `resume_improvement` | 面试证据驱动的简历改进建议 | 指向 `user`、`resume`、`interview_session` | `target_path`、原文/建议/证据、基础/应用版本、`pending/accepted/rejected` |
| `retrieval_chunk` | 可恢复的检索 chunk 与 embedding | `scope_type + scope_id` 是逻辑作用域，不设物理外键 | `ordinal`、`content_hash`、`embedding_model`、`embedding_dimensions`、`embedding_json` |
| `async_job` | 异步作业状态、幂等和租约恢复 | `user_id -> user.id`；`subject_id` 为业务对象逻辑关联 | `job_id`、`idempotency_key`、`status`、`attempts`、投递/开始/结束时间、截断错误 |
| `interview_message` | 会话消息和逐题评分 | `session_id -> interview_session.id` | `role`、`content`、`seq_num`、`score`、`hint` |
| `interview_stage` | 四阶段时间线 | `session_id -> interview_session.id` | `stage_name`、`started_at`、`ended_at` |
| `score_history` | 三维能力趋势 | 指向 `user`、`interview_session` | `technical_score`、`expression_score`、`logic_score` |
| `user_weakness` | 报告沉淀的薄弱点 | 指向 `user`、`interview_session` | `category`、`description` |
| `llm_provider_config` | 后端可用 Provider 目录 | `provider_key` 唯一 | `display_name`、`base_url`、`available_models`、`enabled` |

## 一致性边界

- `schema.sql` 同时承担全新建库和幂等结构升级；CI 使用 MySQL 8.4 模拟缺失新表/字段后重复执行。
- `data.sql` 只维护生产安全参考数据和旧 Provider 值，不创建 demo 用户/会话；`data-dev.sql` 只创建 local/dev 账号。
- `resume_improvement` 接受建议时同时依赖建议状态 CAS、字段原文检查和 `resume.document_version` CAS。
- `retrieval_chunk` 的逻辑 scope 可对应 resume 或 session；不使用跨类型物理外键，应用 Port 负责作用域隔离。
- `async_job` 的 `subject_id` 当前对应报告 session；幂等键和状态 claim 是运行时真源，不据此宣称消息零丢失。
