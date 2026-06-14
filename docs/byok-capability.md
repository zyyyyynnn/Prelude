# OpenAI-compatible BYOK 能力说明

状态：已实现。本文档用于说明 BYOK 能力边界、接口行为、验证方式与后续增强入口，不作为临时执行文档。

## 能力边界

系统支持用户级 OpenAI-compatible BYOK 配置。用户提供 endpoint root、API Key 与运行模型后，聊天、流式面试与 RabbitMQ 报告生成均使用用户保存的配置。

内置 Provider 配置仍作为兼容路径存在，但 README、docs 和论文资产不把任何特定厂商、临时 endpoint 或临时运行模型写成默认推荐。

## 用户流程

- 用户输入 OpenAI-compatible endpoint root 与 API Key。
- 系统调用 `{root}/models` 自动检测模型列表。
- 用户选择运行模型并保存配置。
- 后续聊天、流式面试与 RabbitMQ 报告生成统一使用用户级 endpoint / API Key / model。

## 后端接口

- `POST /api/user/llm-config/discover-models`：使用请求中的 endpoint root 与 API Key 调用 `{root}/models`，返回去重后的模型列表，不保存 API Key。
- `GET /api/user/llm-config`：返回 providerKey、baseUrl、model、hasApiKey 与脱敏 Key 状态。
- `PUT /api/user/llm-config`：保存 `openai-compatible` 的 baseUrl、model 与加密后的 API Key；空 Key 不覆盖，`__CLEAR__` 表示清空。
- `POST /api/user/llm-config/test`：使用用户保存的 OpenAI-compatible 配置发起一次配置连通性测试。

## 数据安全

- API Key 加密保存。
- 模型发现接口不保存 API Key。
- 日志、文档和证据不记录 API Key 明文。
- 具体运行模型仅作为用户选择或测试参数，不作为默认推荐。

## 验证结果

- `discover-models` 可返回去重后的模型列表。
- 保存配置后，GET 接口返回 baseUrl、model、hasApiKey 和脱敏 Key。
- 配置测试可走用户级 OpenAI-compatible 配置。
- `/finish → RabbitMQ → BYOK LLM → report_ready` 功能链路通过。
- 不宣称高并发压测、生产级可靠投递或消息零丢失。

## 限制与后续增强

- 当前验证仅证明一次真实 BYOK 功能链路可用，不代表性能基准。
- 未完成公网高并发压测、生产级可靠投递验证或消息零丢失证明。
- 后续可增强模型列表缓存、错误分类提示与多 Provider 配置管理。
