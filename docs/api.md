# API 接口说明

## 通用约定

- 业务接口统一返回 `Result<T>`：`code`、`message`、`data`。
- 除登录、注册、健康检查、Provider 列表和 dev fixture 重置外，其余接口均需携带 `Authorization: Bearer <token>`。
- SSE 流式接口使用 `event: message` 推送文本片段，使用 `event: error` 返回流式错误。

## 认证与基础数据

### `POST /api/auth/register`

注册用户。

请求示例：

```json
{
  "username": "demo",
  "password": "123456",
  "email": "demo@example.com"
}
```

### `POST /api/auth/login`

登录并返回 JWT。

请求示例：

```json
{
  "username": "demo",
  "password": "123456"
}
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "jwt-token"
  }
}
```

### `GET /api/health`

健康检查。

### `GET /api/position/list`

获取岗位模板列表。

## LLM 与用户设置

### `GET /api/llm/providers`

获取启用的 Provider 与内置模型列表。

- 当前支持内置 Provider 和 `openai-compatible` 自定义兼容接口。自定义兼容接口的模型列表通过用户 endpoint 自动检测，不依赖固定推荐模型。

### `GET /api/user/llm-config`

获取当前用户的 Provider、endpoint、模型和脱敏 API Key 状态。

响应字段包括：

```json
{
  "providerKey": "openai-compatible",
  "baseUrl": "https://example.com/v1",
  "model": "model-id",
  "hasApiKey": true,
  "apiKeyMasked": "sk-***"
}
```

### `POST /api/user/llm-config/discover-models`

检测 OpenAI-compatible endpoint 的可用模型列表。该接口只检测，不保存 API Key 或 endpoint。

请求示例：

```json
{
  "baseUrl": "https://example.com/v1",
  "apiKey": "sk-xxx"
}
```

响应示例：

```json
{
  "providerKey": "openai-compatible",
  "baseUrl": "https://example.com/v1",
  "models": ["model-a", "model-b"]
}
```

### `PUT /api/user/llm-config`

保存当前用户的 Provider、endpoint、模型和 API Key。

请求示例：

```json
{
  "providerKey": "openai-compatible",
  "baseUrl": "https://example.com/v1",
  "model": "model-id",
  "apiKey": "sk-xxx"
}
```

说明：

- `apiKey` 留空（不传或空字符串）表示不修改现有 Key。主动清空需传 `"__CLEAR__"`。
- `providerKey` 为 `openai-compatible` 时，`baseUrl` 必填，保存 endpoint root，不保存完整 `/chat/completions`。
- 常规模式下 Key 使用后端加密后保存。
- dev fixture 开启时不会保存真实 Key，只保存本地夹具占位值。

### `POST /api/user/llm-config/test`

测试当前用户已保存的模型配置。

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "providerKey": "openai-compatible",
    "model": "model-id",
    "ok": true,
    "message": "模型配置测试通过"
  }
}
```

说明：

- 常规模式会发起一次轻量模型调用。
- dev fixture 开启时直接返回本地夹具配置可用，用于 local/dev 验收。

### `GET /api/user/profile`

获取用户资料。

### `PUT /api/user/profile`

更新用户资料或密码。

## 简历与面试

### `POST /api/resume/upload`

上传 PDF 简历并进行文本提取与结构化解析。

约束：

- 仅支持 PDF。
- 单文件不超过 10MB。
- 当前只支持可提取文本的 PDF，不支持纯图片扫描件 OCR。
- 后端保存完整提取文本，但发送给模型解析的文本会做长度保护，避免超长简历导致模型上下文超限。

### `GET /api/resume/list`

获取当前用户简历列表。

### `DELETE /api/resume/{resumeId}`

删除当前用户未被面试会话使用的简历。

### `POST /api/interview/start`

创建面试会话。

请求示例：

```json
{
  "resumeId": 1,
  "positionId": 1
}
```

### `GET /api/interview/sessions`

获取当前用户的面试会话列表。

### `GET /api/interview/{sessionId}/messages`

获取会话消息、阶段时间线和报告信息。

### `POST /api/interview/{sessionId}/chat`

发送回答并接收 SSE 流式面试官回复。

查询参数：

- `autoStart=true`：由面试官主动发起第一问。

请求示例：

```json
{
  "content": "我的项目主要负责后端接口、SSE 流式问答和报告生成。"
}
```

SSE 示例：

```text
event: message
data: 请继续说明你在 SSE 链路中如何保证消息顺序。

```

错误示例：

```text
event: error
data: 模型服务暂不可用，请稍后重试或切换 Provider

```

### `POST /api/interview/{sessionId}/stage`

推进面试阶段。

合法顺序：

```text
warmup -> technical -> deep_dive -> closing
```

请求示例：

```json
{
  "stageName": "technical"
}
```

### `POST /api/interview/{sessionId}/finish`

结束面试并通过 RabbitMQ 异步生成结构化评估报告，同时沉淀评分历史和薄弱点数据。旧会话中的纯 Markdown 报告继续兼容展示。

## 数据分析

### `GET /api/analytics/radar`

获取能力雷达图数据。

### `GET /api/analytics/trend`

获取评分趋势数据。

### `GET /api/analytics/weaknesses`

获取薄弱点统计数据。

## Dev fixture 接口

### `POST /api/dev-fixtures/reset`

重置 local/dev 验收数据夹具。

说明：

- 仅 `app.dev-fixtures.enabled=true` 时可用。
- 会重建 dev test account、简历、进行中会话、已完成会话、评分历史和薄弱点数据。
- 默认会话包含 1 场 `Java 后端工程师` 进行中会话，以及 `Java 后端工程师`、`前端工程师`、`算法工程师` 各 1 场已完成会话。
