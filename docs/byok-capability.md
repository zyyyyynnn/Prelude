# BYOK 协议边界

Prelude 的面试 LLM 目录固定为一个默认 Provider 与三个用户级 BYOK 协议。Provider 展示名由后端 `/api/llm/providers` 返回，前端不维护名称映射。

| providerKey | 类型 | 用户根地址映射 | 模型选择 |
| --- | --- | --- | --- |
| `deepseek` | 默认 Provider | 使用系统配置 | 后端目录 |
| `openai-responses` | BYOK | `{root}/responses` | `{root}/models` 检测或手动填写 |
| `openai-chat-completions` | BYOK | `{root}/chat/completions` | `{root}/models` 检测或手动填写 |
| `anthropic-messages` | BYOK | `{root}/messages` | 手动填写 |

## 配置语义

- 用户保存根地址、模型与一个当前作用域的 API Key；根地址通常以 `/v1` 结尾。
- 保存时允许输入完整的当前协议端点，后端会归一化为根地址；输入其他协议的端点后缀会被拒绝。
- API Key 使用 AES-GCM 加密保存。空 Key 不覆盖同一作用域的已有 Key，`__CLEAR__` 明确清空。
- providerKey 或归一化根地址变化时，不复用旧 Key；测试新作用域必须重新填写 Key。
- 模型发现只读取模型目录，不保存根地址或 Key。Anthropic Messages 不假设自定义网关提供 OpenAI 风格的模型目录。

## 调用边界

- OpenAI Responses 使用 `input`、`max_output_tokens`、`output` 内容数组与 `response.output_text.delta` 流事件。
- OpenAI Chat Completions 使用 `messages`、`max_tokens`、`choices[].message.content` 与 `choices[].delta.content`。
- Anthropic Messages 将 system 消息提升为 `system` 字段，使用 `x-api-key`、`anthropic-version` 与 `content_block_delta`。
- 三个 BYOK provider 调用失败时直接返回配置错误，不参与系统 Provider fallback，也不会把用户 Key 转发到其他 Provider。

## 出站安全

- 生产默认只允许 HTTPS 443，拒绝 URL 凭证、query、fragment 和未批准端口。
- 配置保存和实际连接都会执行 DNS 校验；任一解析结果落入 loopback、私网、link-local、metadata、保留地址或受限 IPv6 过渡网段即拒绝。
- HTTP 客户端不跟随重定向，设置连接/写入/读取/总调用超时；普通响应与流式响应累计上限均为 2 MiB，单条流事件额外限制为 256 KiB。
- `application-local.example.yml` 仅为本地代理显式放宽 HTTP、私网与端口；生产默认值不继承该放宽。
- 上游响应正文和底层异常不会返回给浏览器，服务端日志不得记录 API Key。

## 接口

- `GET /api/llm/providers`：返回启用且存在真实协议实现的 Provider。
- `GET /api/user/llm-config`：返回当前 providerKey、baseUrl、model、hasApiKey 与脱敏 Key。
- `PUT /api/user/llm-config`：保存当前用户的协议、根地址、模型与加密 Key。
- `POST /api/user/llm-config/discover-models`：按 `providerKey` 检测 OpenAI 协议的 `{root}/models`。
- `POST /api/user/llm-config/test`：使用草稿或已保存配置发起轻量连通性调用，不持久化草稿。

生产启动幂等执行 `schema.sql` 与 `data.sql`；旧 `openai`、`openai-compatible` 与 `anthropic` 选择会分别归并到 `openai-chat-completions` 与 `anthropic-messages`，同时保留用户根地址和加密 Key。
