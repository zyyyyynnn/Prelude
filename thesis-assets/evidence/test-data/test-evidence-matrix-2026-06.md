# 测试数据与论证证据矩阵（2026-06）

## 1. 文件定位

* 本文件用于阶段 2.11A 测试证据核对；
* 本文件不等同于正文；
* 本文件不冻结引用编号；
* 本文件不生成图表；
* 本文件只判定哪些测试证据可支撑第三、四、五章。

## 2. 证据来源清单

| 文件路径 | 证据类型 | 内容摘要 | 对应模块 | 可支撑章节 | 当前状态 | 风险说明 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| [demo-2026-04-25.json](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/demo-2026-04-25.json) | JSON 原始测试数据 | 记录 2026 年 4 月本机回环 Demo 模式运行的原始 JSON 交互结果 | Demo 模式 / 模拟数据 | 第五章 | 历史存档 | 仅限本地隔离验证，不能代表真实网络环境 |
| [demo-2026-04-25.md](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/demo-2026-04-25.md) | 测试记录 Markdown | 包含 4 月 25 日 TC-01 ~ TC-09 本地测试步骤与响应时延指标 | Demo 模式 / 前后端联调 | 第五章 | 历史存档 | 数据属于旧版本，仅作对比参考 |
| [demo-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/demo-2026-06.md) | 测试记录 Markdown | 记录 6 月最新本机 Windows 全栈环境测试结论与历史真实 API 记录对比 | Demo / Real 模式 | 第五章 | 活跃资产 | 明确标示了大部分高并发与 SSE 并发为“未实测” |
| [env-2026-04-24.md](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/env-2026-04-24.md) | 环境配置 Markdown | 记录 4 月份的操作系统、基础依赖和 Maven/Vite 验证命令 | 测试环境 | 第五章 | 历史存档 | 仅记录历史状态 |
| [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) | 环境配置 Markdown | 记录 6 月最新 Node.js、JDK21、MySQL 等版本，以及最新的 Maven 测试和 Vite 构建通过日志 | 测试环境 / 构建记录 | 第五章 | 活跃资产 | 仅代表单机开发环境，不代表分布式部署环境 |
| [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) | 功能测试用例 | 列出 TC-01 到 TC-09 的测试项、预期结果与实际测试结论（含“待实测”项） | 核心业务模块 | 第五章 | 活跃资产 | 部分高可用及语音多模态为“待实测”状态，不可夸大 |
| [real-llm-api-2026-05-27-redacted.json](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.json) | JSON 原始测试数据 | 历史真实 API 功能链路原始记录（已脱敏） | 公网模型接口 / Real 模式 | 第五章 | 补充历史对照 | 数据产生于 5 月底，不作为当前默认 Provider、默认模型或正文固定模型依据 |
| [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) | 性能测试记录 | 详细记录历史真实 API 调用时延（如 finish 报告生成 118s）及伴随 Bug 修复细节（已脱敏） | 公网接口 / 状态机控制 | 第四章 / 第五章 | 补充历史对照 | 时延数据与当前最新代码存在时间差，仅做补充和架构对照 |
| [01-demo-proxy.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/01-demo-proxy.md) | Bug 修复证据 | 描述 Vite 代理 ECONNREFUSED 错误、根因及 `.env.demo` 配置文件修复证据 | 前端代理 / 环境配置 | 第四章 / 第五章 | 活跃资产 | 属于配置与环境鲁棒性问题，不得夸大为系统并发能力 |
| [02-mysql-preflight.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/02-mysql-preflight.md) | Bug 修复证据 | 描述 Hikari 连接超时错误、根因及 start-demo 脚本中前置校验修复证据 | 数据库连接 / 启动脚本 | 第四章 / 第五章 | 活跃资产 | 属本地前置校验增强，不得夸大为生产环境热部署 |
| [package-2026-04-24.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/package-2026-04-24.md) | Bug 修复草案 | 整理答辩或论文用 4 类典型 Bug（代理、MySQL、执行策略、页面抽动） | 异常排查 | 第四章 / 第五章 | 活跃资产 | 仅用于辅证，非主线业务能力 |
| [impl-2026-04-24.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-04-24.md) | 代码实现证据 | SSE 问答通道后端核心 `SseEmitter` 的底层处理与断流重连逻辑代码 | 流式服务 | 第四章 | 活跃资产 | 需核对与当前最新代码是否存在漂移 |
| [impl-2026-05-31.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-05-31.md) | 代码实现证据 | 正则提取评分的柔性降级和 JSON 块剔除逻辑（已被 Structured Output 替代） | 数据解析 | 第四章 | 已弃用 | 旧版正则已被 Structured Output 重构替代，仅能做背景描述 |
| [impl-2026-06-02.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-02.md) | 代码实现证据 | Structured Output 替换正则、客户端静默核对、Redis 滑动窗口限流代码片段 | 高可用 / 容灾 / 缓存 | 第四章 | 活跃资产 | 代码设计精妙，但缺乏大规模并发压测数据支撑，仅可作架构宣称 |
| [impl-2026-06-05.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-05.md) | 代码实现证据 | 前端 rAF 刷新节流、AbortController 互斥锁、useVoiceMedia 音频 Composable 剥离代码 | 前端优化 | 第四章 | 活跃资产 | 缺乏高频多端压测的实测数据，仅能从帧率对齐与机制上进行论证 |
| [impl-2026-06-13-rabbitmq.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-13-rabbitmq.md) | 代码实现证据 | RabbitMQ 报告任务队列集成（Producer / Consumer / Config / 幂等保护） | 报告异步任务队列 | 第四章 | 活跃资产 | 仅本地 Docker Compose 基础链路联调通过，不等同公网高并发或生产级可靠投递 |
| [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (2.3) | 容器联调记录 | Docker Compose 环境关闭 Demo 模式，通过用户级 OpenAI-compatible BYOK 完成模型发现、配置保存、配置测试与 `/finish → RabbitMQ → ReportJobWorker → 真实 LLM 调用 → report_ready` 功能链路 | 报告异步任务队列 / LLM 调用 / BYOK | 第四章 / 第五章 | 活跃资产 | 只证明一次真实 BYOK 功能链路可用；具体模型为运行参数，不作为仓库默认配置；不代表公网性能、高并发压测、生产级可靠投递或消息零丢失 |
| [package-2026-04-25.md](file:///E:/Prelude/thesis-assets/defense/package-2026-04-25.md) | 答辩材料说明 | 答辩材料包的内容大纲与定位 | 答辩相关 | 独立答辩 | 活跃资产 | 独立于正文构建，仅作为辅助对齐 |
| [script.md](file:///E:/Prelude/thesis-assets/defense/script.md) | 答辩讲稿 | 5-8分钟答辩汇报口径与演讲稿，包含 Demo 性能阐述与 Bug 排查 | 答辩相关 | 独立答辩 | 活跃资产 | 口径需随着测试矩阵的最终审查结论进行严格收缩，严禁夸大 |
| [slide-map.md](file:///E:/Prelude/thesis-assets/defense/slide-map.md) | PPT 映射表 | 12页 PPT 大纲，建立幻灯片与论文章节、代码及测试数据映射 | 答辩相关 | 独立答辩 | 活跃资产 | 需在此处对齐最新 June 数据 |

## 3. 功能测试证据矩阵

| 模块 | 测试项 | 已有证据 | 缺口 | 是否可写入正文 | 推荐写法 | 禁止写法 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **用户登录 / 注册** | 用户创建、JWT 生成与鉴权 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-07, TC-09)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-01, TC-02) | 多端登录强制下线与续签的压力测试数据 | 可写入正文 | 系统实现了用户注册、基于密码学加盐的哈希存储以及标准的 JWT 会话管理，功能验证通过。 | 系统支持数万并发用户的安全登录与无缝 JWT 自动刷新续签。 |
| **简历上传 / 解析** | PDFBox 文本提取、大模型提取结构化数据 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-01)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-05) | 公网复杂多页 PDF 批量提取的成功率统计 | 可写入正文 | 系统支持 PDF 格式简历上传，底层利用 Apache PDFBox 进行文本抽取，并提交至 LLM 进行信息段结构化匹配。 | 系统可在并发环境下百分之百正确提取任何排版的简历。 |
| **岗位匹配** | 提取简历技能与预置岗位匹配度 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-01)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-06) | 无 | 可写入正文 | 根据简历解析的技能点与岗位数据库中的模板指标进行相似度对齐，辅助后续面试提问生成。 | 系统通过智能匹配算法能精准预测求职者与岗位的胜任度。 |
| **模拟面试对话** | 破冰、技术、深挖、收尾四个阶段的流转 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-04)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-06, TC-07, TC-08, TC-09) | 长对话截断后信息的细微遗忘测试 | 可写入正文 | 面试流程按设定阶段状态流转，系统能结合简历内容及上下文回答生成连续性追问。 | 系统完全消除了大语言模型的幻觉，能完美掌控所有面试阶段的合理推进。 |
| **面试报告 / 反馈** | 提取大模型评估并生成三维评分和薄弱点建议 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-06)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-10, TC-11) | 无 | 可写入正文 | 面试结束后，后端请求模型对全程对话进行多维总结，提取结构化评分并利用 ECharts 绘制雷达图。 | 系统实现了极其精准且绝对无偏差的人才评估模型，可直接作为企业录用标准。 |
| **语音或多模态能力** | 实时录音发送与转码展示 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-03) | **本地 Demo 环境未集成真实 ASR 服务，该项为“待实测”** | 限制性写入 | 架构设计上规划了基于 WebSocket 的语音包采集与 ASR 映射通道，但实际运行与端点检测（VAD）指标尚未实测。 | 系统实现极低延迟的实时智能语音合成和百分百高精度的语音识别。 |
| **流式响应 / SSE** | 前端 SSE 流式流接收与展示 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-02)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-07, TC-08) | 极端闪断下的重连丢包率统计 | 可写入正文 | 系统利用 HTTP SSE 协议分流下发字符片段，前端使用 ReadableStream 机制进行高可用逐字展示。 | 系统 SSE 连接支持上千并发同时进行流式响应且零掉帧。 |
| **LLM 调用 / BYOK** | 多模型 Provider 适配、OpenAI-compatible endpoint 模型发现、AES 密钥加密保存 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-08)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-03, TC-04)<br>[env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (2.3) | 灾备切换的自动检测反应时间 | 可写入正文 | 支持在用户侧单独配置 OpenAI-compatible endpoint、API Key 和运行模型；API Key 加密持久化，模型列表可由 endpoint 自动发现。 | 系统在公网高并发拥堵时能进行零时延无感智能多路模型秒级切换。 |
| **PDF / 文件解析** | PDFBox 文本提取 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-01)<br>[real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (TC-05) | 大文件、扫描件和图文混排的提取率 | 可写入正文 | 使用 PDFBox 进行本地文本流读取和清洗，对标准排版 PDF 实现了高速抽取。 | 系统具有强大的版面分析能力，可完美解析各类扫描版与异常加密的 PDF。 |
| **限流、熔断、重试** | Resilience4j 熔断、Redis 滑动窗口限流机制 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) (TC-08)<br>[impl-2026-06-02.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-02.md) | **高并发触发熔断限流的压力测试结果与数据文件** | 仅可描述实现机制，不可写成测试通过 | 与限流熔断相关的逻辑，应限缩在架构设计与防护机制的描述中。 | 系统在网关层集成了基于 Redis 的拦截限流机制和基于 Resilience4j 的熔断容灾保护逻辑。 | 经实测，系统限流和熔断器能在极端高流量涌入时自动响应并有效拦截流量。 |
| **异步报告任务队列** | `/finish` → RabbitMQ → `@RabbitListener` → `report_ready` | [impl-2026-06-13-rabbitmq.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-13-rabbitmq.md)<br>[env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (2.3) | **公网高并发 / 大规模压测数据** | 限制性可写（可写 Docker Compose 基础链路与一次真实 API Key 功能链路通过） | 中等 | 已通过代码、Docker Compose 基础链路联调与一次关闭 Demo 模式的真实 API Key 功能链路记录 `/finish → RabbitMQ → ReportJobWorker → report_ready` 的端到端流程；Redis 仍承担限流、缓存与状态辅助职责。 | 系统已具备生产级可靠的消息队列能力，能在高并发场景下保证报告任务的零丢失。 |
| **会话状态枚举与 RabbitMQ 一致性** | `interview_session.status` 三态机（`ongoing / generating / finished`） | [impl-2026-06-13-rabbitmq.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-13-rabbitmq.md)<br>[database-table-dictionary-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md) (`interview_session.status`) | 新 volume 重建验证 | 可写入正文 | 源 DDL `backend/src/main/resources/schema.sql` 中 `interview_session.status` 已从 `enum('ongoing','finished')` 扩展为 `enum('ongoing','generating','finished')`，并附幂等 ALTER 兼容旧环境。`generating` 为 RabbitMQ 报告任务已发布但尚未完成消费的中间态。 | `interview_session.status` 已完成生产级可靠的状态机迁移，状态切换零失败。 |
| **系统构建与部署验证** | 后端测试与前端打包 | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (2.1, 2.2) | 容器环境的集成构建测试 | 可写入正文 | 经由本地 Maven 虚拟线程运行通过全部单元测试，Vite 前端顺利完成生产级打包，环境校验闭环。 | 项目已在生产服务器进行了完整的大规模集成部署验证并稳定运行。 |

## 4. 性能与边界测试矩阵

| 测试项 | 当前证据 | 是否真实实测 | 可写程度 | 风险级别 | 阶段 3 写作边界 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **构建验证** | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md): `mvn -q test` 成功；`npm run build` 打包耗时约 14.73s。 | 是 | 充分可写 | 无风险 | 可列为第五章的“系统构建与编译验证”，作为工程合格性指标。 |
| **Demo 响应链路** | [demo-2026-04-25.md](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/demo-2026-04-25.md): PDF 上传解析接口耗时 36ms，SSE TTFB 59ms，总时延 198ms。 | 是 (仅限本机回环隔离环境) | 限制性可写 | 中等 (若未说明 Demo Twin 模式，则属夸大) | 必须明确指出是 **Demo Twin 本机数据隔离与模拟数据环境** 下的时延，以此说明当前系统在隔离演示态的前后端响应时延。 |
| **SSE 流式响应** | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md): 首轮面试官开场词流式耗时 1.84s (从首个 chunk 到流结束)。 | 是 | 限制性可写 | 极低 | 可以在第五章描述单人真实测试状态下的 SSE 通道开启速度及提问流式响应，作为流畅性体验的依据。 |
| **PDF 解析耗时** | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md): 简历文本提取耗时 54ms。 | 是 | 限制性可写 | 极低 | 明确该时延**仅包含 PDFBox 文本提取**的消耗，不含大模型进行结构化分析的延迟。 |
| **LLM 响应延迟** | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md): 外部模型连通性时延 3.20s；多维评估报告生成时延 118.35s。 | 是 | 限制性可写 | 极低 | 必须在第五章作为“历史真实 API 调用性能”展示，客观记录大模型长对话总结需要百秒级的处理时延，体现真实测试诚实底线；不得写成当前默认模型或固定推荐模型。 |
| **高并发压测** | 无。 | 否 | **不可写成已完成验证** | 极高 | 论文中**不得宣称完成高并发压力测试**。在第五章中应略过具体性能报告，或诚实指出“高并发性能待后续上线验证”。 |
| **限流熔断极端场景** | [impl-2026-06-02.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-02.md) 拦截器与熔断配置代码。 | 否 (仅有代码，无压测数据) | 仅可描述实现机制，不可写成测试通过 | 高 | 第四章只能用于分析熔断与限流的架构方案及防刷机制；第五章中不得声称已对其进行了极端压测。 |
| **长连接断线重连** | [impl-2026-06-02.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/impl-2026-06-02.md) 客户端指数退避与静默核对设计。 | 否 (仅有机制，无闪断丢包率压测) | 仅可描述实现机制，不可写成测试通过 | 中等 | 第四/五章可以描述在发生长连接瞬时闪断时，系统具备指数退避及客户端静默核对的自愈机制。 |
| **数据库连接稳定性** | [02-mysql-preflight.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/02-mysql-preflight.md) 启动前置连接性核查。 | 是 (仅验证脚本层前置核验生效) | 限制性可写 | 极低 | 第四/五章中将 Hikari 报错与启动脚本的前置校验作为部署稳定性证据，不得引申为高并发数据库稳定性。 |
| **中间件服务状态** | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) 记录了 Redis 与 RabbitMQ 在 Docker Compose 环境中均已运行：Redis 承担限流、缓存与状态辅助职责；RabbitMQ 已接入报告生成异步任务队列。 | 是 (仅限本地 Docker Compose 集成开发环境) | 限制性可写 | 极低 | 说明中间件在本地集成开发环境中已通过 `/finish → RabbitMQ → @RabbitListener → report_ready` 的端到端基础链路联调验证；不得断言其作为高负载集群的高可用指标。 |
| **真实 BYOK 报告链路** | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (2.3) 记录了 Docker Compose 容器环境下关闭 Demo 模式、通过用户级 OpenAI-compatible BYOK 完成模型发现、配置保存、配置测试后，`/finish → RabbitMQ → ReportJobWorker → 真实 LLM 调用 → report_ready` 完成一次功能链路验证。 | 是 (单次功能链路验证) | 限制性可写 | 中等 | 可以写成“在当前网络与运行模型条件下完成一次真实 BYOK 功能链路验证”；不得写成性能基准、稳定性基准、高并发压测、生产级可靠投递或模型推荐。 |

## 5. 第五章可写结论边界

| 可写内容 | 需要降调的内容 | 禁止写入内容 |
| :--- | :--- | :--- |
| * 功能测试用例覆盖主要模块，TC-01 ~ TC-09 本地测试逻辑闭环；<br>* Demo Twin / 隔离验证已通过的实际范围，确保演示环境安全隔离且数据可恢复；<br>* 构建验证、环境配置（Node v24, JDK21, Maven 3.9）、关键功能链路通过，可作为工程验证依据；<br>* 已有 bug 修复证据（如 Vite 代理 ECONNREFUSED、Hikari 连接报错）可作为问题排查与工程改进依据。 | * 面试评估报告只能说明系统具备“自动分析多轮长对话、提取结构化评分并分类提取薄弱点描述”的能力，**不代表大模型评分与真实人类面试考官具有一致的绝对客观性**；<br>* 流式响应时延（如 TTFB 59ms / 1.84s）只能说明链路实现畅通，**不代表在高并发高负载环境下的网络及流式稳定性**；<br>* 限流、熔断、重试只能在有代码和配置文件支撑的前提下描述架构实现机制，**不代表已完成在大流量涌入时的压力阻断验证**。 | * 已完成系统高并发压力测试与流量阻断实测；<br>* 已完成生产环境或真实业务并发性能验证；<br>* 已证明大模型 AI 评分绝对公平无偏差，可完美替代人工面试官；<br>* 经实测本系统可提高企业招聘效率或招聘质量（因没有此类真实统计）；<br>* 已完成 SSE 长连接极端并发压力测试；<br>* 已完成限流熔断在极端拥堵场景下的压力触发与数据记录。 |

## 6. 是否需要补测

| 补测项 | 是否必要 | 推荐优先级 | 原因 | 是否阻塞阶段 3 |
| :--- | :--- | :--- | :--- | :--- |
| **功能测试缺口 (ASR语音模块)** | 是 | P1 | 语音模块（TC-02, TC-03）目前在本地 Demo 下为“待实测”状态，需确认在正文中是否大篇幅描写该能力。若要细化描写，必须补充相应接口的 Mock 日志或真实 API 抓包数据。 | 否 (可通过在正文中做限制性描写避开) |
| **截图登记缺口 (docs/images)** | 是 | P1 | docs/images/ 下的 7 张系统截图未登记到 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中。本阶段不处理，但在进入后续正文排版（阶段 2.11B / 阶段 3）前必须补齐登记。 | 否 (本阶段只核对，下阶段再行补齐) |
| **高并发压力测试** | 否 | P2 | 限于硬件与沙盒测试条件，压测不属于本科毕业设计必需项。若补测不仅耗时大，且需要引入 JMeter 等工具。可通过收缩论文宣称口径（不写高并发通过）来直接规避。 | 否 (不阻塞，但严格限制了论文第五章的写作边界) |
| **SSE 长连接断线重连** | 否 | P2 | 静默核对机制已在代码层面实现，断线重连的异常模拟可通过前端控制台断开网络验证。若能补充前端控制台的异常流重试日志，可将该项转为“已通过”。 | 否 |
| **限流熔断触发实测** | 否 | P2 | 可以通过 JMeter 简单压测几秒来触发 Lua 限流并在 Redis 中记录 limit key 作为辅助证据，从而让第五章的证据更充实。但这属于可选优化项。 | 否 |
