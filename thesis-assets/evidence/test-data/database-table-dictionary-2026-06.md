# 数据库表结构说明（2026-06）

## 1. 文件定位

* 本文件承接图3.2中省略的字段细节；
* 图3.2只展示核心实体关系与主外键依赖，不展示全部字段，以保证用图的清晰度与正文可读性；
* 本文件不是正文；
* 本文件不冻结正文表号；
* 阶段 3 写作时，可根据本字典提供的数据，在论文第五章或第三章的系统设计部分整理为正式的表结构说明表格。

## 2. 表结构摘要

| 表名 | 主要用途 | 主键 | 关键外键 / 逻辑关联 | 关键字段与类型 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **user** | 用户账号及全局模型参数表 | `id` (bigint) | 无 | `username` (varchar), `password` (varchar), `email` (varchar), `llm_provider` (varchar), `llm_model` (varchar), `llm_api_key_encrypted` (varchar), `llm_thinking_depth` (varchar) | 存储登录凭证（加盐哈希密码）以及用户自定义的外部 LLM 节点配置（API Key 使用 AES-256-GCM 密文存储）。 |
| **resume** | 用户上传的 PDF 简历解析数据表 | `id` (bigint) | `user_id` (bigint) -> `user(id)` | `file_name` (varchar), `parsed_skills` (text), `parsed_projects` (text), `raw_text` (mediumtext) | 存放从 PDF 提取的原始文本（供 RAG 切片使用），以及经 LLM 结构化抽取的技能与项目 JSON 数据。 |
| **position_template** | 系统预置岗位匹配及 Prompt 模板表 | `id` (bigint) | 无 | `name` (varchar), `system_prompt` (text) | 存储标准岗位要求（如 Java、前端、算法）以及对应的系统级提示词，用于限制面试官提问范围。 |
| **interview_session** | 用户模拟面试会话生命周期表 | `id` (bigint) | `user_id` -> `user(id)`<br>`resume_id` -> `resume(id)`<br>`position_id` -> `position_template(id)` | `target_position` (varchar), `llm_provider` (varchar), `llm_model` (varchar), `status` (enum), `summary` (text), `summary_report` (text), `jd_text` (mediumtext) | 记录一次面试会话，保存会话状态（进行中/已结束）、使用的模型快照、上下文压缩摘要及最终评估报告。 |
| **interview_message** | 会话中面试问答详细消息记录表 | `id` (bigint) | `session_id` (bigint) -> `interview_session(id)` | `role` (enum), `content` (text), `seq_num` (int), `score` (tinyint), `hint` (varchar) | 存储全部消息队列（system, user, assistant）。包含针对求职者回答的单项临时评分及改进建议。 |
| **interview_stage** | 面试进行阶段跟踪日志表 | `id` (bigint) | `session_id` (bigint) -> `interview_session(id)` | `stage_name` (enum), `started_at` (datetime), `ended_at` (datetime) | 跟踪阶段流转状态（破冰 warmup -> 技术考察 technical -> 深挖项目 deep_dive -> 结束 closing）。 |
| **score_history** | 评估生成的得分历史，用于看板绘制 | `id` (bigint) | `user_id` -> `user(id)`<br>`session_id` -> `interview_session(id)` | `technical_score` (tinyint), `expression_score` (tinyint), `logic_score` (tinyint) | 记录每次面试结算时的三维能力评分（技术、表达、逻辑），用以生成 ECharts 趋势图。 |
| **user_weakness** | 评估报告中提炼的薄弱点列表 | `id` (bigint) | `user_id` -> `user(id)`<br>`session_id` -> `interview_session(id)` | `category` (varchar), `description` (text) | 大模型根据历史问答智能提炼的求职者薄弱项与复习建议。 |
| **llm_provider_config** | 系统全局支持的外部模型渠道配置表 | `id` (bigint) | 无 | `provider_key` (varchar), `display_name` (varchar), `base_url` (varchar), `available_models` (text), `enabled` (tinyint) | 全局定义的 LLM 端点配置，包含模型别名映射和支持的模型列表 JSON。 |

## 3. 图3.2 省略字段说明

为了保持 E-R 图的物理间距，以下类型字段被统一移出图表展示，但在底层数据库 schema 中依然生效：
1. **审计时间戳**：各表中的 `created_at` 字段以及 `interview_stage` 中的 `started_at`、`ended_at` 通用时间戳已在图中省去。
2. **海量长文本与 JSON**：`resume` 中的 `parsed_skills` / `parsed_projects` / `raw_text`，`interview_session` 中的 `summary` / `summary_report` / `jd_text`，以及 `interview_message` 中的 `content` 在图中不予展开。
3. **安全凭证与大体积参数**：`user` 表的 `password`（BCrypt 密文）与 `llm_api_key_encrypted`（AES 密文），以及各中间件字段如 `llm_max_tokens` 被隐藏以保证图表信息纯粹。
