# 答辩正式讲稿（5-8 分钟版）

各位老师好，我的毕业设计题目是《基于 LLM 的模拟面试与简历诊断系统》。本系统面向求职准备场景，目标是把简历解析、岗位匹配、阶段化模拟面试、评估报告、历史回放和能力分析串成一条完整的训练闭环。

首先是选题背景。传统面试准备通常依赖人工辅导或简单题库，反馈成本较高，也难以持续记录每次训练中的表现变化。随着大语言模型的发展，系统可以根据简历、岗位和历史回答生成更接近真实面试的追问，并在结束后给出结构化反馈。因此，本课题希望实现一个可本地运行、可展示、可复盘的模拟面试系统。

系统总体采用前后端分离架构。前端使用 Vue 3、TypeScript、shadcn-vue 与 Tailwind CSS，负责登录、简历管理、面试工作台、数据看板和设置弹窗。后端使用 Spring Boot，提供认证、简历解析、面试会话、流式问答、报告生成和能力分析接口。数据层使用 MySQL 保存用户、简历、会话、消息、阶段、评分和薄弱点，Redis 用于限流、缓存和状态辅助，RabbitMQ 用于报告生成异步任务队列。外部能力主要包括 PDFBox 文本提取、内置 Provider 配置和 OpenAI-compatible BYOK；用户可以配置自定义 endpoint、API Key，并通过模型发现选择运行模型。

当前运行入口已经收敛为两类：`start-dev.bat` 用于日常开发、人工验收和答辩演示，Docker 管理 MySQL、Redis、RabbitMQ，本机运行后端和 Vite 前端；`start-docker.bat` 用于全量容器化验证。dev fixture 提供本地可重置验收数据，不依赖公网模型，也不污染生产口径。旧 Demo Twin 只作为历史对照材料保留，不再作为当前演示路线。

在业务流程上，用户登录后可以上传 PDF 简历，系统通过 PDFBox 提取文本并结合岗位模板创建面试会话。面试过程按照破冰、技术、深挖和收尾阶段推进。用户提交回答后，前端通过 `fetch + ReadableStream` 接收后端 SSE 流式内容，页面可以逐步展示模型回复。当会话变长时，系统会压缩早期上下文，保证提示词长度可控。面试结束后，系统生成结构化评分与 Markdown 评估报告，并支持在数据看板中查看能力雷达图、评分趋势和薄弱点统计。

本系统的核心实现主要有四点。第一是 Structured Output 报告解析。旧版方案使用正则提取评分，容易因模型输出格式波动而失败；当前方案采用结构化输出、Jackson 反序列化和分数 clamp 降级，降低格式波动导致解析失败的概率。第二是流式面试链路。后端将模型返回内容按片段通过 SSE 推送给前端，前端通过 `requestAnimationFrame` 缓冲渲染，减少流式展示时的视觉卡顿。第三是可靠性保护。系统引入 Redis 限流、Resilience4j 熔断降级和 SSE 异常恢复机制；其中 openai-compatible BYOK 失败会显式暴露，不会静默切换到系统 Key。第四是异步报告生成。`/finish` 接口快速返回生成中状态，报告生成任务通过 RabbitMQ 队列异步处理，worker 完成后通过 SSE 推送 `report_ready` 事件。

测试方面，当前证据覆盖功能用例 TC-01 到 TC-12，包括 PDF 简历解析、SSE 文本流式响应、语音链路容错、报告生成、BYOK 设置、fallback 边界、质量门禁、消息序号与阶段系统消息一致性。工程门禁方面，CI 已包含 whitespace diff check（PR 路径用 merge-base 取得 diff 起点）、Sentrux 架构规则、后端测试、JaCoCo report artifact、npm audit、前端构建、BYOK verify、dark verify、`verify:ui` UI 静态 guardrail、`verify:tokens` token schema、`verify:a11y` 仅 critical axe violations；`capture:visual` 作为 artifact-only（`continue-on-error: true`）上传 17 个场景的 PNG 供人工 review，Playwright 在 CI 复用系统 Microsoft Edge channel。需要说明的是，JaCoCo 当前只生成覆盖率报告，不设置阈值；BYOK verify 使用 mock API 验证设置流程，不代表真实公网模型性能；`verify:ui` 是 UI 静态 guardrail 与 semantic sizing 红线扫描，不等同全量视觉回归，不能据此宣称 UI 完全无缺陷；`verify:a11y` 只 fail critical axe violations，serious 仍记入 backlog，不等同完整 WCAG 2 AA 达标；`capture:visual` 不做像素 diff，不作为 blocking gate。

近期也完成过一次 Docker Compose 容器环境下的真实 BYOK 功能链路验证：通过用户级 OpenAI-compatible 配置完成模型发现、配置保存、配置测试，并跑通一次 `/finish → RabbitMQ → ReportJobWorker → 真实 LLM 调用 → report_ready`。这说明功能链路可用，但不代表公网性能基准、高并发压测、生产级可靠投递或消息零丢失。

开发过程中遇到的典型问题主要有两类。第一类是历史阶段的前端代理错连问题，说明环境变量和代理目标必须作为启动契约显式校验。第二类是数据库服务未就绪导致后端启动失败，说明一键启动脚本不仅要串联命令，也要承担中间件、端口和关键密钥的前置检查职责。这些问题已作为工程复盘证据保留，但它们只说明环境鲁棒性改进，不能夸大为高并发能力。

总结来说，本课题完成了一个可运行的模拟面试系统，重点体现了完整业务闭环、SSE 流式体验、Structured Output 结构化评分、Redis 限流与 Resilience4j 熔断、RabbitMQ 异步报告生成、Docker 编排和证据化测试。当前不足是语音真实 ASR/TTS 端到端性能、限流熔断触发数据和大规模并发压测仍未完成。后续可以继续补充真实语音链路测试、批量测试和部署监控。

我的汇报结束，请各位老师批评指正。
