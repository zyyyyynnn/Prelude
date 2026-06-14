# 答辩材料包

> 状态：2026-06 口径重写版。文件名保留 2026-04-25 是为了兼容既有索引，不代表内容仍停留在 4 月。

## 一、演示路线

1. 启动 Demo Twin：运行 `.\start-demo.bat`，说明 Demo 与真实模式端口、数据库和前端代理隔离。
2. 登录演示账号：使用 `demo / 123456` 进入系统。
3. 主工作台（空状态）：展示简历选择、岗位匹配、JD 匹配输入区。
4. 简历流程：上传 PDF 简历，说明后端 PDFBox 文本提取与结构化解析入口。
5. 面试流程：创建会话，按破冰、技术、深挖、收尾阶段推进问答；展示 SSE 流式渲染效果。
6. 报告流程：生成 Markdown 评估报告（Structured Output 解析），在主工作台报告预览中查看结果。
7. 数据看板：进入 `/analytics`，展示能力雷达图、评分趋势折线和薄弱点统计。
8. LLM 配置：通过侧边栏设置菜单打开弹窗，展示用户级 Provider、模型、API Key 配置与连接测试。
9. 设置流程：展示用户设置页的邮箱与密码维护。

## 二、系统亮点

| 亮点 | 答辩表述 |
| --- | --- |
| 完整业务闭环 | 系统覆盖登录、简历解析、岗位匹配、模拟面试、报告生成、回放和能力分析，不是单点页面展示。 |
| SSE 流式体验 | 面试回答采用 `fetch + ReadableStream` 接收流式内容，前端通过 `requestAnimationFrame` 缓冲渲染，用户能看到渐进式返回效果。 |
| Structured Output | 报告评分采用 JSON Schema 结构化提取，替代旧版正则方案，配合防御性反序列化与安全降级（默认 6 分），杜绝格式幻觉导致的解析崩溃。 |
| 高可用机制 | 引入 Redis 限流（Lua 令牌桶）、Resilience4j 熔断降级（自动切换备用 Provider）、SSE 指数退避重连（静默核对 + 分流策略）。 |
| 异步报告生成 | `/finish` 接口快速返回状态，报告生成任务通过 RabbitMQ 队列异步解耦；`InterviewServiceImpl.finish(...)` 将 session 状态置为 `generating` 并通过 `RabbitTemplate.convertAndSend` 发布 `ReportJobMessage`，`ReportJobWorker` 通过 `@RabbitListener` 消费队列，完成后 SSE 推送 `report_ready` 事件；Redis 回归限流、缓存和状态辅助职责。本次验证为本地 Docker Compose 基础链路联调，不等同于公网高并发压测，不证明生产级可靠投递或消息绝不丢失。 |
| Demo Twin 机制 | Demo 模式使用独立端口、数据库和演示数据，便于答辩复现，同时不污染真实模式。 |
| 用户级 LLM 配置 / BYOK | 支持用户维护 Provider、自定义 OpenAI-compatible endpoint、运行模型和 API Key，Key 使用 AES-256-GCM 加密保存。 |
| 全栈容器化 | Docker Compose 一键编排 MySQL 8.4 + Redis + 后端 + Nginx 前端，配合 Prometheus + Grafana 可观测性看板。 |

## 三、关键难点

| 难点 | 解决思路 |
| --- | --- |
| 流式响应与消息落库一致性 | 后端在流式输出结束后统一收口 AI 回复，前端识别完成态并恢复按钮状态。 |
| 上下文长度控制 | Sliding Window Memory：超过 15 轮时触发摘要压缩，上下文锁定为 `[System/Stage Prompts] + [RAG 简历背景] + [Session Summary] + [最近 4 轮原声对话]`。 |
| 评分格式幻觉 | Structured Output + Jackson 反序列化 + clampScore(1,10) + 安全降级 6 分，降低正则解析脆弱性与格式波动风险。 |
| 网络闪断重连 | SSE 中断时先静默核对服务器消息状态，成功态跳过重试，回滚态触发指数退避重连（1s-5s，最多 3 次）。 |
| Demo 与真实模式隔离 | 通过 `.env.demo`、Demo 数据库、端口和启动脚本校验保证演示链路可复现。 |
| LLM Provider 熔断 | Resilience4j CircuitBreaker（滑动窗口 10，阈值 50%），超时熔断后自动切换备用 Provider 并广播 fallback 事件。 |

## 四、主讲 Bug

| Bug | 讲解重点 | 证据 |
| --- | --- | --- |
| Demo 登录代理 `ECONNREFUSED` | `.env.demo` 缺失导致 Vite 代理错连真实端口，修复后将 Demo 代理目标作为启动契约校验。 | `thesis-assets/evidence/bug-evidence/01-demo-proxy.md` |
| MySQL 未就绪导致后端启动失败 | Hikari 初始化失败不是业务代码问题，脚本层提前校验数据库连接和密钥配置。 | `thesis-assets/evidence/bug-evidence/02-mysql-preflight.md` |

## 五、追问问答口径

| 可能追问 | 回答口径 |
| --- | --- |
| 为什么使用 SSE，而不是 WebSocket？ | 本系统主要是服务端向前端持续推送模型文本，交互方向以单向流为主，SSE 实现更轻量，浏览器原生支持也更适合文本流式输出。语音场景已规划 WebSocket 双向流，但当前仍为待实测能力。 |
| Demo Twin 是否会影响真实业务？ | 不会。Demo 使用独立端口、`.env.demo`、Demo 数据库和 `/api/demo/reset` 重建数据，真实模式不自动插入演示用户。 |
| 如果外部模型服务不可用怎么办？ | 系统通过 Resilience4j 熔断器自动检测 Provider 超时或 5xx 错误，触发后自动切换备用 Provider 并向前端广播切换通知。Demo Twin 可保证答辩演示不依赖公网模型。 |
| API Key 如何保存？ | 当前采用用户级配置，并通过 AES-256-GCM 加密后保存；论文中可说明这是毕业设计阶段的安全方案，生产环境仍需接入更完整的密钥管理。 |
| 测试数据是否代表真实公网性能？ | 不代表。当前测试验证的是 Demo Twin 本机业务闭环、历史真实 API 补充测试，以及 2026-06-14 Docker Compose 容器环境关闭 Demo 模式后的一次真实 BYOK 功能链路。该记录不代表公网性能基准、高并发压测，也不作为模型推荐依据。 |
| RabbitMQ 是否已经实现？ | 代码层面已接入：依赖 `spring-boot-starter-amqp`、`docker-compose.yml` 含 `prelude-rabbitmq`（5672/15672）、`InterviewServiceImpl.finish(...)` 通过 `RabbitTemplate.convertAndSend` 发布 `ReportJobMessage`、`ReportJobWorker` 通过 `@RabbitListener` 消费并完成报告生成后 SSE 推送 `report_ready`、`interview_session.status` DDL 已扩展为 `ongoing / generating / finished`。本地 Docker Compose 基础链路联调已通过；2026-06-14 又在容器环境关闭 Demo 模式，通过用户级 OpenAI-compatible BYOK 完成模型发现、配置保存、配置测试与一次 `/finish → RabbitMQ → ReportJobWorker → 真实 LLM 调用 → report_ready` 功能链路。Redis 回归限流/缓存/状态辅助职责。**不证明**：公网高并发压测、生产级可靠投递、消息绝不丢失；具体模型仅是运行参数，不作为默认配置或模型推荐依据。 |
| 语音交互是否已实现？ | 语音 WebSocket 双向流已完成基础架构搭建（VoiceWebSocketHandler、VoiceServiceImpl），但 STT/TTS 外部 API 的真实低延迟表现仍为待实测能力，不得写成公网已完成。 |
| 系统最大不足是什么？ | 主要不足是语音交互和部分性能指标仍为待实测状态，且当前没有做大规模并发压测；后续可补语音真实链路测试、批量测试和部署环境监控。 |

## 六、演示风险与备用方案

| 风险 | 备用方案 |
| --- | --- |
| MySQL 未启动 | 先运行启动脚本准备阶段；若失败，根据脚本提示启动 MySQL 或修正本地配置。 |
| Redis 未启动 | 启动脚本会探测 6379 端口；若失败，手动启动 Redis 后重试。 |
| 浏览器缓存旧页面 | 强制刷新或重新运行 `start-demo.bat`，确认前端端口为 `5174`。 |
| 外部模型不可用 | 使用 Demo Twin 的脚本化演示数据完成全流程展示；Resilience4j 熔断器自动切换备用 Provider。 |
| 页面网络请求失败 | 先检查 `8081/api/health` 和 `5174/login`，再执行 `reset-demo.ps1` 重建演示数据。 |

## 七、测试依据

当前主测试依据（2026-06 口径）：

| 文件 | 用途 |
| --- | --- |
| `thesis-assets/evidence/test-data/env-2026-06.md` | 测试环境与构建验证 |
| `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 功能测试用例 TC-01 ~ TC-09 |
| `thesis-assets/evidence/test-data/demo-2026-06.md` | Demo Twin 业务验证 |
| `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 测试证据矩阵 |
| `thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md` | 历史真实 API 功能链路记录（已脱敏） |

历史对照（已归档，仅作参考）：
- `thesis-assets/evidence/test-data/archive/demo-2026-04-25.md` — 4 月 Demo Twin 本机回环数据
- `thesis-assets/evidence/test-data/archive/env-2026-04-24.md` — 4 月环境记录
