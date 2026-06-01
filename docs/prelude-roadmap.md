# Prelude · 后续演进路线清单

---

## Phase 1 · 技术债清偿与核心体验加固

### 1. Structured Output 替换正则评分提取
**价值** 彻底解决大模型输出格式幻觉导致正则解析失败的技术债，实现评分数据 100% 结构化入库，提升 AI 调用层契约的确定性。

**技术选型与硬约束**
- **量纲对齐锁死（1-10分制）**：为保持历史持久化数据兼容性，避免前端 `AnalyticsView.vue` 中最大值为 10 的 ECharts 雷达图与趋势图发生视觉“高度塌陷”，**Schema 中的 `technical`, `expression`, `logic` 评分标准必须维持 1-10 整数范围**。严禁改为 1-5 分制。
- **路由层扩展（LlmRouter 重构）**：改动范围除 `InterviewServiceImpl.java` 外，**必须扩展 `LlmRouter.java` 的 `chatWithSnapshot` 接口签名**，使其允许上层业务（如 finish 报告生成）注入自定义的扩展参数（如 `response_format: { type: "json_object" }`），并由内部 `buildInvocation` 方法将其安全合入下发给 `AbstractOpenAiCompatibleProvider` 的 `extraParams` 中。
- **防御性数据解析**：彻底删除 `extractScore` 正则逻辑。在解析层，通过注入的 `ObjectMapper` 反序列化为目标 DTO。代码必须使用带有默认安全降级值的 `try-catch` 块包裹（例如反序列化彻底崩溃时，默认赋予安全分 6 分并记录 Error 级别日志），**绝对禁止**抛出未捕获异常导致 `/finish` 接口触发 HTTP 500。

**改动范围**
- Backend: `LlmRouter.java` (扩展参数传递通道)、`InterviewServiceImpl.java` (生成与反序列化逻辑)、相关 DTO 结构。

**完成标准 (DoD)**
1. 后端 `java.util.regex` 正则评分提取代码被干净移除。
2. 连续 20 次使用带有复杂符号或代码块的恶劣文本进行报告生成测试，系统结构化提取成功率必须达 100%，且 ECharts 图表数据充盈、无刻度塌陷。

---

### 2. SSE 优雅重连与幂等状态核对
**价值** 完美兼容后端 `InterviewServiceImpl.java` 闪断异常时删除未完成消息的回滚机制（`deleteById`），消除客户端重连时盲目重发导致的“重复提问”和“并发锁（SESSION_LOCKS）阻塞”风险。

**技术选型与硬约束**
- **静默核对优先原则（防重复提交）**：当前端 `streamInterviewChat` 捕获到流式连接中断时，**绝对禁止立即重发 Payload**。前端必须首先发起一次**静默数据核对**——触发调用 `fetchInterviewMessages(sessionId)` 接口。
- **状态对齐分流策略**：
  - **分流 A（闪断成功态）**：若静默核对发现服务端**已成功持久化**最新的 User 消息（或 AI 已响应），说明后端事务未回滚且流程正常，前端直接使用服务端返回的消息栈全量覆写本地响应式数组，平滑移出 Loading 态，**直接跳过本次网络重试**。
  - **分流 B（事务回滚态）**：若静默核对证实服务端**不存在**该条最新消息（已被后端 Catch 逻辑成功删除回滚），前端方能安全地激活指数退避重连机制（Exponential Backoff，建议初始延迟 1000ms，最大 5000ms，最多重试 3 次）并重新携带原 Payload 调度 `streamInterviewChat` 接口。
- **异步任务消息适配（衔接 Phase 4 异步生成）**：重连核对逻辑必须能够识别异步生成状态（当 API 返回 `status: "generating"` 时），此时前端不发起重连，而是重新拉起对当前会话 SSE 管道的监听，并正确注册 `event: report_ready` 处理器以等待异步报告生成通知。
- **UI 零跳动要求**：整个静默核对与退避重试期间，消息列表禁止清空、禁止白屏、输入框保持聚焦锁死。仅在会话尾部外挂轻量“连接修复中...”提示，对齐后自动消除。

**改动范围**
- Frontend: `api/interview.ts` (封装静默核对与重连逻辑)、`MessageThread.vue` (处理无缝状态过渡)。

**完成标准 (DoD)**
1. 在大模型流式输出中途手动切断网络，控制台可见客户端首先执行静默消息核对（`fetch`）。
2. 当触发分流 A 时，界面平滑恢复，不产生任何重复的聊天气泡；当触发分流 B 时，安全触发退避重发，流程无缝衔接。

---

## Phase 2 · AI 工程深化与 UX 体验突破

### 3. Redis 与 WebSocket 基础服务搭建
**价值** 为后续的 Judge 频控、异步任务解耦及双向语音流提供统一的分布式底层与网络通信支持，避免核心特性开发时的依赖倒置。

**技术选型与硬约束**
- **基础设施引入**：后端引入 `spring-boot-starter-data-redis` 并配置单机 Redis 连接池；引入 `spring-boot-starter-websocket`。
- **配置持久化与隔离**：Redis 端口（默认 6379）与密码必须在 `application-local.yml` 中进行环境隔离，提供 Demo 状态和 Real 状态的 Database Index 隔离。
- **WebSocket 统一握手拦截**：配置 WebSocket 握手拦截器，校验 JWT 令牌并将 `userId` 注入至 WebSocket Session Attributes 中，保证双向通道的安全审计。

**改动范围**
- Backend: `pom.xml` (引入 Redis 与 WebSocket 依赖)、新建 `com.interview.config.RedisConfig`、`com.interview.config.WebSocketConfig`。

**完成标准 (DoD)**
1. 执行编译与单测无异常，Redis 与 WebSocket 握手端点均可在本地通过健康检查。
2. 启动脚本检测到本地 Redis 未拉起时，有友好的标准错误输出提示。

---

### 4. LLM-as-Judge 实时答题评分
**价值** 在面试对话中引入辅助评估模型，异步评估单条回答质量并通过 SSE 实时推送评分与改进提示（如“回答偏理论，缺少实际并发优化场景支撑”），提升系统智能感与 RLHF 数据收集能力。

**技术选型与硬约束**
- **并发与频控（Redis 信号量）**：Judge 任务必须由上一步搭建的 Redis 维护分布式信号量，确保同一 `userId` 下单会话的 Judge 调度串行执行，防止瞬间高频点击导致大模型 API 欠费或限流。
- **SSE 连接复用与生命周期**：
  - 后端在 AI 面试官流式回复结束后，**禁止立即调用 `emitter.complete()`**。
  - 将 `emitter` 句柄安全传递给异步 Judge 线程。
  - Judge 线程处理完毕后，向 SSE 写入 `event: judge` 类型事件，payload 格式严格为 `{ "score": 1-10, "hint": "..." }`，最后由 Judge 异步线程执行 `emitter.complete()` 正常释放连接。
- **抗抖动渲染**：前端 [MessageThread.vue](file:///e:/Prelude/frontend/src/components/workspace/MessageThread.vue) 监听 `judge` 事件，通过 ID 锚定对应的用户消息气泡，在右下角挂载评分 Badge（无需挂载 Monaco Editor，仅渲染轻量 Badge 提示），并利用 CSS 预设高度防止气泡伸缩造成页面滚动条跳动。

**改动范围**
- Backend: [InterviewServiceImpl.java](file:///e:/Prelude/backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java) (控制 SSE 完成时机及异步 Judge 调度)。
- Frontend: [MessageThread.vue](file:///e:/Prelude/frontend/src/components/workspace/MessageThread.vue) (事件监听与气泡渲染)。

**完成标准 (DoD)**
1. 用户发送回答 → 3 秒内气泡右下角自动浮现 Judge Badge。
2. 控制台无 `emitter` 已关闭但仍写入 Judge 数据的 `IOException` 异常。

---

### 5. Sliding Window Memory (上下文动态压缩)
**价值** 解决长会话 Token 累积带来的性能损耗与历史上下文遗忘，保障大模型在长周期多阶段面试中的专注度。

**技术选型与硬约束**
- **触发阀值**：当会话轮数超过 10 轮（即 20 条消息）时激活。
- **摘要 Agent (Summarization Agent)**：
  - 每新增 5 轮交互，触发异步任务对第 1-8 轮的非系统消息进行摘要总结，更新至数据库中。
  - 结构存储：在 [schema.sql](file:///e:/Prelude/backend/src/main/resources/schema.sql) 中采用 `IF NOT EXISTS` 动态为 `interview_session` 表新增 `summary` 字段。
- **Context 组合约束**：发送给大模型的上下文格式锁死为：`[System Prompt & 当前 Stage Prompt] + [Session Summary] + [最近 4 轮原声对话记录]`。确保面试官的角色特征与当前阶段要求（如 `STAGE_PROMPTS` 中的特定指令）不因摘要压缩而丢失。
- **隐私保护**：摘要 Prompt 中加入严格的脱敏指示，严禁在汇总中携带手机号、邮箱、身份证等用户隐私数据。

**改动范围**
- Backend: [schema.sql](file:///e:/Prelude/backend/src/main/resources/schema.sql) (结构迁移)、[InterviewServiceImpl.java](file:///e:/Prelude/backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java) (上下文重构与摘要线程调度)。

**完成标准 (DoD)**
1. 对话长度超过 15 轮时，大模型仍能准确定位前 2 轮中提及的核心简历项目细节。
2. 后台调用 Token 监控显示，长会话后期单次交互所消耗的 Context Tokens 趋于常数线，无指数级增长。

---

### 6. 基于 RAG 的精准 JD 匹配与解析
**价值** 解决单次简历解析 `limitText(1800)` 截断导致的细节丢失问题，同时支持匹配用户自定义的岗位 JD，使 AI 面试官的发问深度直接命中岗位招聘痛点。

**技术选型与硬约束**
- **轻量向量检索（HNSW 内存索引）**：
  - 引入零外部依赖的 Java 本地向量检索（如通过 `hnswlib-jna` 或本地实现 `HNSW` 内存向量空间），以绝对避免在单机/演示环境中依赖复杂的向量数据库。
- **文本切片与向量化**：
  - 利用 PDFBox 提取完整文本，通过字符分割算法进行切片（Size: 512, Overlap: 50）。
  - 使用 OkHttp 异步调用 DeepSeek / OpenAI Embedding API 获取文本向量存入内存 HNSW。
- **混合检索逻辑**：
  - 发问前对简历切片和 JD 文本进行 Hybrid Search（向量相似度 + 文本关键词匹配），提取 Top-5 关联碎片注入 Context。
- **DoD 评测指标（可度量化）**：
  - 提取生成的提问文本，比对上传 JD 中的特定技术实体（提取前 10 个名词标签，如“Spring Cloud”、“Redis”）。生成的问题必须命中其中至少一个实体，以证明检索有效。

**改动范围**
- Backend: `ResumeServiceImpl.java` (分块向量化逻辑)、新增本地 RAG 检索 Service、实体结构扩展。
- Frontend: [InterviewComposer.vue](file:///e:/Prelude/frontend/src/components/workspace/InterviewComposer.vue) (增加 JD 粘贴/上传入口)。

**完成标准 (DoD)**
1. 即使上传超过 50 页的超长简历，AI 仍能根据提取出的深层项目切片进行精准追问。
2. 使用 3 套不同的特化岗位 JD（如：“高性能网关”、“大模型微调”）进行测试，AI 生成的提问在对应领域的特化技术词命中率达 100%。

---

## Phase 3 · 前端体验突破与客户端性能加固

### 7. 评估报告 PDF 导出（排版优化级）
**价值** 生成排版精美、契合纸张版式的 PDF 报告，支持候选人本地保存及分享，显著增强界面成果展示的专业度。

**技术选型与硬约束**
- **渲染排版策略（前端 html2canvas + jspdf）**：
  - 避免图片模糊：配置 `html2canvas` 导出时的 `scale: 2` (或 `devicePixelRatio: 2`)，并开启图片平滑。
  - **分页版式锁定（硬约束）**：为防范 ECharts 雷达图、分数走势折线图或薄弱点列表在页面折页处被物理切断，在 [AnalyticsView.vue](file:///e:/Prelude/frontend/src/views/AnalyticsView.vue) 及相关样式文件中引入 CSS 属性 `page-break-inside: avoid` 进行排版控制。
  - 动态高度计算：使用 JS 精确计算 `.markdown-body` 高度，按 A4 纸比例（595.28 x 841.89 pt）进行动态分页剪切与多页组装。

**改动范围**
- Frontend: 新增 `utils/pdf.ts` (导出核心工具类)、[InterviewView.vue](file:///e:/Prelude/frontend/src/views/InterviewView.vue) (报告展示侧导出按钮绑定)。

**完成标准 (DoD)**
1. 点击“导出报告”即时触发 PDF 下载。
2. 导出的多页 PDF 中，所有 ECharts 报表与薄弱点卡片均保证整体分页，**无跨页切割**、**无字体模糊**现象。

---

### 8. 语音实时互动流（Voice-to-Voice WebSocket 双向流）
**价值** 模拟真实的语音/电话面试交互场景。用户按住说话即可进行口语答题，AI 实时生成语音面试官回复，极大增强沉浸感。

**技术选型与硬约束**
- **双向流式传输与延迟（硬性指标）**：
  - 前端利用 `MediaRecorder` 采集 PCM 音频切片，通过已搭载的 WebSocket 通道流式上传。
  - 后端接收流分包调用 `Whisper API` 转文字，喂给 `LlmRouter` 异步生成文本。
  - 文字流输出即时送入 TTS 引擎（如 `Edge-TTS` 或 `Azure Speech API`），生成音频流回传。
  - **延迟对齐锁死**：从用户说话结束（VAD 检测或松开按键）到听到 AI 首字节音频的**总首字节音频延迟必须小于 3.0 秒**。
- **外部 API 熔断降级（防御性策略）**：
  - 当 STT / TTS 外部 API 发生网络超时（大于 3 秒）或返回错误码时，系统必须**自动无感知降级**为“纯文本+流式字幕渲染”模式，在界面中弹出轻量提示（“网络状况不佳，已为您切回文字模式”），绝对禁止因 API 崩溃导致会话挂起。

**改动范围**
- Backend: `VoiceInterviewController.java` (语音 WebSocket 接入层)、`VoiceService.java` (语音识别与合成网关)。
- Frontend: 新增 [VoiceVisualizer.vue](file:///e:/Prelude/frontend/src/components/workspace/VoiceVisualizer.vue) (音频波形组件)、[InterviewView.vue](file:///e:/Prelude/frontend/src/views/InterviewView.vue) (语音交互模式集成)。

**完成标准 (DoD)**
1. 用户松开按键，3.0s 内听到 AI 面试官流式发音回复。
2. 手动断开 STT/TTS 接口网络，系统可在 3s 内自动切回文字模式，保持当前对话上下文不丢失。

---

## Phase 4 · 基础设施演进与高可用加固

### 9. 异步任务解耦与削峰（Redis List/Stream 架构）
**价值** 彻底解决生成 Markdown 评估报告和执行 Judge 打分时，长达 15s+ 的网络请求同步挂起问题，防止用户连接泄露和高并发期间的服务器资源耗尽。

**技术选型与硬约束**
- **轻量化消息队列（MQ）**：
  - 直接基于 Redis 依赖，利用 `Redis List` (或 `Redis Stream` 保证可靠消费) 作为异步任务队列。
- **任务分发与状态流转**：
  - 调用 `/finish` 时，后端立即写入 Redis 任务队列，并向下游返回 `{ "status": "generating", "jobId": "..." }`，维持请求在 200ms 内快速返回。
  - 消费端（Worker 线程池）异步拉取任务，进行报告生成与入库持久化。
- **推送对齐（SSE / WebSocket 通知）**：
  - 报告处理完毕后，工作线程向当前 Session 的 SSE 链接推送 `event: report_ready`，携带完整的报告内容，前端自动从 Loading 状态平滑过渡到渲染好的报告页面。

**改动范围**
- Backend: 新增 `com.interview.config.QueueConfig`、`com.interview.service.ReportJobWorker` (异步消费服务)、[InterviewServiceImpl.java](file:///e:/Prelude/backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java) (改造 `/finish` 写入流程)。
- Frontend: [InterviewView.vue](file:///e:/Prelude/frontend/src/views/InterviewView.vue) (增加报告生成中异步等待状态遮罩)。

**完成标准 (DoD)**
1. 点击“结束面试”按钮后，页面请求在 200ms 内完成，出现精美的“报告生成中”AI 诊断沙漏动效。
2. 异步处理完毕后，页面无需人工刷新，依靠 SSE 推送平滑展示最终评估报告。

---

### 10. LLM 自适应网关限流与熔断降级（Resilience4j）
**价值** 保护用户级 API Key 额度，并在大模型 Provider（如 DeepSeek）遭遇服务器拥堵或断网时，自动平滑切换备用通道，保障线上演示系统零宕机率。

**技术选型与硬约束**
- **用户级限流（Redis + Lua 令牌桶）**：
  - 在 API 入口处使用 Redis + Lua 脚本实现分布式令牌桶限流，限定单用户 LLM 发问频率上限（如 `max 10 rpm`），超限返回 `429 Too Many Requests`。
- **智能熔断降级（Resilience4j CircuitBreaker）**：
  - 后端引入 `resilience4j-spring-boot3` 包。
  - 为 `LlmRouter` 代理的网络调用（OkHttpClient）挂载熔断器：当 Provider A（如自配 DeepSeek API）连续超时率或 HTTP 5xx 错误率超过 50% 时，触发熔断器“Open”状态。
  - **自动灾备切换（Fallback Routing）**：熔断触发后，自动读取数据库中可用的备用 `llm_provider_config`（如 OpenAI 或 System Default Provider）进行降级代理，同时向前端推送轻量级状态提示（如“已为您切换至备用通道”）。

**改动范围**
- Backend: [pom.xml](file:///e:/Prelude/backend/pom.xml) (引入 Resilience4j)、[LlmRouter.java](file:///e:/Prelude/backend/src/main/java/com/interview/llm/LlmRouter.java) (包裹熔断切流逻辑与 Redis 频控拦截)。

**完成标准 (DoD)**
1. 模拟 DeepSeek API 域名解析失败或故意返回超时，熔断器自动触发，无缝无缝切换到 OpenAI 继续完成面试。
2. 恶意并发刷接口时，系统返回友好限流 JSON，不造成后端线程池爆满。

---

### 11. 全栈容器化部署与监控体系（Prometheus + Grafana）
**价值** 提升生产环境可用性与可观测性，使毕设项目具备企业级微服务部署与监控运维水准。

**技术选型与硬约束**
- **Actuator 监控指标采集**：
  - 引入 `spring-boot-starter-actuator` 并集成 `micrometer-registry-prometheus`。
  - 自定义注册 Metrics 指标：追踪 LLM 接口调用耗时（P50/P90/P99 折线）、Token 每日累计消费 Counter、Provider 错误频次等。
- **Nginx 反向代理与资源映射**：
  - Nginx 统一代理容器组前端资源，并配置 `/api/` 路由转发规则代理后端 8080/8081 端口，处理跨域与 HTTPS SSL 加密。
- **Docker Compose 多容器编排（一键拉起）**：
  - 编写多阶段构建的 `Dockerfile`，将 MySQL 8.0 容器、Redis 容器、后端 Jar 包容器、前端 Nginx 容器联合编译。
  - 配置 `depends_on: mysql: condition: service_healthy`，确保 MySQL 就绪并执行初始化脚本后，后端再行挂载，防止启动顺序混乱导致连接超时崩溃。

**改动范围**
- Backend: [pom.xml](file:///e:/Prelude/backend/pom.xml) (引入 Actuator 与 Micrometer)、新增 `Docker` 配置文件、新增 `prometheus.yml`。
- Frontend: 新增 Nginx 配置、Dockerfile 文件。
- Project Root: 新增 `docker-compose.yml`。

**完成标准 (DoD)**
1. 在根目录下执行 `docker-compose up --build -d`，在 5 分钟内完成全套微服务拉起、MySQL 数据库初始化和数据初始化动作（允许 MySQL 启动前置缓冲），并能流畅完成登录及对话。
2. 浏览器打开 `/actuator/prometheus` 格式输出规范，Grafana 看板数据图表同步刷新。

---

## 落地顺序与依赖建议

| 顺序 | 功能模块 | 归属阶段 | 技术依赖前置条件 | 优先级与落地理由 |
|------|------|------|------|------|
| 1 | **Structured Output** | Phase 1 | 无 | **最高（P0）**：清偿正则解析技术债，保障评分入库稳定性。 |
| 2 | **SSE 重连与状态核对** | Phase 1 | 无 | **最高（P0）**：消除网络闪断带来的会话截断、重复入库及异步生成衔接问题。 |
| 3 | **Redis 与 WS 基础设施搭建** | Phase 2 | 无 | **高（P1）**：作为 Redis 环境与 WebSocket 协议的基础底座，解耦后续的 Judge 频控与语音流依赖倒置。 |
| 4 | **LLM-as-Judge 实时评分**| Phase 2 | 依赖 Redis | **高（P1）**：核心 AI 互动特性，利用 Redis 信号量串行锁防止频控透支。 |
| 5 | **Sliding Window 上下文压缩**| Phase 2 | 数据库 Schema 修改 | **中（P2）**：长周期面试核心加固，优化长会话 Tokens 成本。 |
| 6 | **异步任务解耦 (Redis MQ)**| Phase 4 | 依赖 Redis | **中（P2）**：彻底解决 finish 阶段生成报告长耗时阻塞问题。 |
| 7 | **PDF 精美导出** | Phase 3 | 无 | **中（P2）**：前台展示特性，由于不涉及 AI 核心流程，优先级从 P1 降至 P2。 |
| 8 | **网关限流与熔断降级** | Phase 4 | Resilience4j + Redis | **中（P2）**：网络异常和恶意防刷的防护手段。 |
| 9 | **RAG 精准 JD 匹配** | Phase 2 | PDFBox 分块算法开发 + HNSW 本地索引 | **低（P3）**：明确使用零外部依赖 HNSW 内存检索，DoD 采用词条匹配客观判定。 |
| 10| **语音实时互动流 (WS)** | Phase 3 | 依赖 WebSocket + 外部语音接口 | **低（P3）**：高风险语音链，总首字节音频延迟要求 < 3s，且需内置接口超时自动降级为文字模式。 |
| 11| **容器化与 Prometheus 监控**| Phase 4 | 全功能模块代码稳定后 | **低（P3）**：基础设施收尾，将启动 DoD 延长至 5 分钟以适配 MySQL 初始化缓冲。 |

---

> 文档版本：2026-06-01 · 由 AI 协助设计与架构优化
