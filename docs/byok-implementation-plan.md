# OpenAI-compatible BYOK 实现说明

状态：已实现，保留为 BYOK 能力说明与后续增强入口。

## 当前阶段问题

旧方案以预置 Provider 与手动模型名为主，体验不等同于成熟 BYOK；真实链路验证也曾依赖临时运行参数，不应成为当前默认能力口径。

## 本轮目标

- 用户输入 OpenAI-compatible endpoint root 与 API Key。
- 系统调用 `{root}/models` 自动检测模型列表。
- 用户选择运行模型并保存配置。
- 后续聊天、流式面试与 RabbitMQ 报告生成统一使用用户级 endpoint / API Key / model。

## 实现范围

- DB：`user.llm_base_url` 保存用户级 endpoint root；API Key 继续使用加密字段。
- 后端：新增模型发现接口，扩展用户 LLM 配置接口，并在 LlmRouter 中对 `openai-compatible` 使用用户级 base URL。
- 前端：全局设置弹窗支持 endpoint 输入、API Key 输入、自动检测模型、模型选择、保存和测试。
- 文档与论文资产：统一为 Provider-neutral / BYOK 口径，不写固定测试模型或临时厂商作为默认能力。

## 安全边界

- API Key 加密保存。
- 模型发现接口不保存 API Key。
- 日志、文档和证据不记录 API Key 明文。
- 具体运行模型仅作为用户选择或测试参数，不作为默认推荐。

## 验收标准

- `discover-models` 可返回去重后的模型列表。
- 保存配置后，GET 接口返回 baseUrl、model、hasApiKey 和脱敏 Key。
- 配置测试可走用户级 OpenAI-compatible 配置。
- `/finish → RabbitMQ → BYOK LLM → report_ready` 功能链路通过。
- 不宣称高并发压测、生产级可靠投递或消息零丢失。
