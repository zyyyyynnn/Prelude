# 阶段 2.11A 测试数据与论证证据核对报告

> **[后续口径说明]**
> 本文保留历史阶段审查语境。2026-06-13 后，RabbitMQ 已接入代码层并完成本地 Docker Compose 基础链路联调；当前报告任务队列事实以 `rabbitmq-report-queue-2026-06-13.md` 与 `final-evidence-lock.md` 为准。

## 1. 阶段边界

* 本阶段不是阶段 3；
* 未修改正文；
* 未刷新图表；
* 未采集截图；
* 未冻结引用编号；
* 未生成 DOCX/PDF；
* 只核对测试数据与论证证据。

## 2. 审查输入

报告编写过程中读取并复核了以下文件与目录资产：
1. 治理规范：[workflow-governance.md](file:///E:/Prelude/thesis-assets/meta/workflow-governance.md)
2. 证据锁定状态：[final-evidence-lock.md](file:///E:/Prelude/thesis-assets/meta/final-evidence-lock.md)
3. 2.10阶段审查：[phase-2.10-evidence-readiness.md](file:///E:/Prelude/thesis-assets/evidence/phase-reports/phase-2.10-evidence-readiness.md)
4. 图表规划：[figure-assets-plan.md](file:///E:/Prelude/thesis-assets/evidence/figure-assets-plan.md)
5. 图表登记：[figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md)
6. 测试数据目录下的所有 Markdown 与 JSON 证据文件：[thesis-assets/evidence/test-data/](file:///E:/Prelude/thesis-assets/evidence/test-data/)
7. Bug 与修复证据：[thesis-assets/evidence/bug-evidence/](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/)
8. 代码片段证据：[thesis-assets/evidence/code-snippets/](file:///E:/Prelude/thesis-assets/evidence/code-snippets/)
9. 答辩辅助材料：[thesis-assets/defense/](file:///E:/Prelude/thesis-assets/defense/)
10. 项目根目录、前后端目录中的配置文件及测试结构：[package.json](file:///E:/Prelude/frontend/package.json)、[pom.xml](file:///E:/Prelude/backend/pom.xml)、[README.md](file:///E:/Prelude/README.md)、[docker-compose.yml](file:///E:/Prelude/docker-compose.yml)、[ci.yml](file:///E:/Prelude/.github/workflows/ci.yml)、[tests/](file:///E:/Prelude/frontend/tests/) 等。

## 3. 当前测试证据概况

| 证据类别 | 当前资产 | 可支撑章节 | 当前状态 | 风险说明 |
| :--- | :--- | :--- | :--- | :--- |
| **功能测试** | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) | 第五章 | 活跃资产 | 部分高可用防护及语音多模态标记为“待实测”，需作限制性描述 |
| **Demo 验证** | [demo-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/demo-2026-06.md)、[demo-2026-04-25.md](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/demo-2026-04-25.md) | 第五章 | 活跃资产/历史对比 | 仅限本机回环隔离环境，不得引申为生产公网表现 |
| **构建验证** | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) 2.1 与 2.2 小节 | 第五章 | 活跃资产 | 仅能证明编译、类型检查和打包顺利完成 |
| **环境配置** | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) 1.0 小节 | 第五章 | 活跃资产 | 代表本机全栈测试环境，不能声称云端分布式部署 |
| **Bug 修复证据** | [01-demo-proxy.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/01-demo-proxy.md)、[02-mysql-preflight.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/02-mysql-preflight.md) | 第四/五章 | 活跃资产 | 仅用于排查和健壮性展示，不可夸大为高可用性能 |
| **代码实现证据** | [structured-output-resilience-2026-06-02.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/structured-output-resilience-2026-06-02.md)、[frontend-streaming-stability-2026-06-05.md](file:///E:/Prelude/thesis-assets/evidence/code-snippets/frontend-streaming-stability-2026-06-05.md) | 第四章 | 活跃资产 | 仅能作为代码逻辑设计和机制的说明，不能替代测试报告 |
| **性能 / 压测证据** | 无 | 第五章 | 缺口 | 缺失真实并发流量压测数据，第五章必须缩减宣称口径 |
| **答辩辅助证据** | [script.md](file:///E:/Prelude/thesis-assets/defense/script.md)、[slide-map.md](file:///E:/Prelude/thesis-assets/defense/slide-map.md) | 独立答辩 | 活跃资产 | 需严格与论文最终确认的测试口径对齐，严禁夸大 |

## 4. 核心模块证据审查

| 模块 | 证据是否充分 | 可写入正文的内容 | 不可写入正文的内容 | 建议 |
| :--- | :--- | :--- | :--- | :--- |
| **用户登录 / 注册** | 充分 | 用户加密哈希存储、JWT 生成、拦截器鉴权与登出功能。 | 承受高并发登录冲击测试、多设备强制踢出的全自动压力数据。 | 描述核心安全机制，展示 TC-07 功能通过。 |
| **简历上传 / 解析** | 充分 | PDFBox 本地提取流、结构化段落抽取及 LLM 岗位要求匹配。 | 扫描版 PDF 全语境复杂提取率统计。 | 强调 PDFBox 在常见电子档简历上的高速解析时延。 |
| **岗位匹配** | 充分 | 匹配相似度算法概念、模型 prompt 引导匹配及岗位库绑定。 | 岗位匹配的绝对准确性。 | 强调其作为面试提问的前置上下文注入价值。 |
| **模拟面试对话** | 充分 | 破冰、技术、深挖、收尾的四阶段状态机转换及上下文控制。 | 状态机完全消除模型幻觉，完美处理用户任意异常非结构化输入。 | 在第三、四章对阶段化面试策略及历史消息窗口截断进行重点描述。 |
| **面试报告 / 反馈** | 充分 | 提取结构化评分，通过正则与 Structured Output 配合解析，以及 ECharts 渲染雷达图。 | AI 评分绝对公平无偏、招聘转化率数据。 | 可引用 TC-06 通过作为闭环证据。 |
| **语音或多模态能力** | 不充分 | WebSocket 语音包定义、 AS 转换机制构想与前端 Composable 结构。 | 真实公网低延迟智能实时语音合成和高精度 ASR 识别。 | **在正文中降调为“架构层设计规划与本地 demo 逻辑，暂未连通公网实测”。** |
| **流式响应 / SSE** | 充分 | 采用 SseEmitter 进行增量推送，前端通过 ReadableStream 进行逐字流式渲染。 | SSE 链路的高并发承载能力和极端丢包率。 | 结合 `interview-sse-resume-context-2026-04-24.md` 代码说明 SSE 的实现机制，TC-04 通道畅通。 |
| **LLM 调用** | 充分 | 支持用户自定义 API Key 并使用 AES-256-GCM 加密，OkHttp 抽象层请求，网关多提供商适配。 | 模型网关层实现无感并发智能灾备切换时延数据。 | 结合配置代码说明 LLM 路由实现。 |
| **PDF / 文件解析** | 充分 | 本地利用 Apache PDFBox 进行标准 PDF 文本流清洗与抽取。 | 任意扫描件或混排图表的高精版面还原。 | TC-01 功能正常，提取耗时 36ms / 54ms。 |
| **限流、熔断、重试** | 不充分 | 熔断配置类（Resilience4j）、限流拦截器（Redis Lua）的代码实现机制。 | 极端高并发压力下的熔断触发记录、限流拦截触发率与系统抗压曲线。 | **严格限缩在“高可用优化架构机制描述”，不可写成测试通过。** |
| **系统构建与部署** | 充分 | 本地单元测试通过（JUnit Mock），前端成功完成打包（Vite）。 | 容器化集群在生产云端部署与自动化运维验证。 | 引用 `env-2026-06.md` 的构建日志作为环境验证闭环指标。 |

## 5. 性能与边界能力审查

| 能力 | 代码或配置证据 | 测试结果证据 | 阶段 3 写法 | 风险 |
| :--- | :--- | :--- | :--- | :--- |
| **SSE / 流式响应** | `InterviewServiceImpl.java` 建立 `SseEmitter` | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (流式耗时 1.84s)<br>[demo-2026-04-25.md](file:///E:/Prelude/thesis-assets/evidence/test-data/archive/demo-2026-04-25.md) (TTFB 59ms) | SSE 交互链路通畅，首字符下发时延正常；流式渲染能有效降频物理帧率。 | 无压测数据，仅限单用户功能流畅验证。 |
| **限流** | `LlmRateLimitInterceptor.java` | 无 (无高并发限流触发日志) | 仅描述系统集成了 Redis 滑动窗口高频限流算法，限缩在机制说明中。 | 夸大为“在高并发下对限流进行了压力实测”。 |
| **熔断** | `application-local.yml` 中 Resilience4j 熔断参数 | 无 (未在生产网络模拟接口雪崩熔断) | 仅能描述网关具有 Resilience4j 熔断灾备机制与切换模型提供商的能力。 | 宣称为“通过了系统雪崩与熔断抗灾测试”。 |
| **重试** | 前端 `InterviewView.vue` 静默核对与指数退避重试 | 无 | 描述流式链接闪断时，系统具有客户端状态对齐、分流幂等核对与自愈逻辑。 | 缺乏大样本闪断重连的数据统计。 |
| **PDF 解析** | `ResumeServiceImpl.java` (PDFBox) | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (文本提取 54ms) | PDFBox 本地解析标准文本耗时极低。 | 该耗时仅针对本地文本流清洗，不代表大模型结构化提取耗时。 |
| **LLM 调用** | `LlmRouter.java` 网关适配器 | [real-llm-api-2026-05-27-redacted.md](file:///E:/Prelude/thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md) (报告生成 118s) | 历史真实 API 长对话大模型评估报告生成耗时较长（百秒级），属正常业务阻断。 | 模型调用易受公网网络波动影响。 |
| **高并发** | 无 | 无 | **论文第五章不宣称通过了高并发性能测试，略过此方面测试数据。** | 虚造测试数据属于学术不端，绝不可出现。 |
| **数据库连接稳定性** | `start-demo.ps1` 数据库连接连通性前置核查 | [02-mysql-preflight.md](file:///E:/Prelude/thesis-assets/evidence/bug-evidence/02-mysql-preflight.md) (Hikari 初始化校验成功) | 系统通过启动脚本对 MySQL 数据库连通性进行了前置校验，增强了本地部署稳定性。 | 本地校验不等同于生产环境高并发连接稳定性。 |
| **中间件服务状态** | `LlmRateLimitInterceptor.java` 拦截机制 | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) (Redis/RabbitMQ服务连通) | 本地测试验证了 Redis 和 RabbitMQ 中间件的正常运行与连通，为缓存限流提供支持。 | 仅限本地单机，无集群高可用数据。 |

## 6. 对 figure-assets-plan.md 的修正建议

图表资产规划方案中存在部分不切实际的口径宣称，为防止后续阶段误导图表刷新与生成，提出以下修正建议：
1. **将 nature-figure 定位降级**：必须将“`nature-figure` 必须参与”降级为“**可选增强工具**”。架构设计仍以原生的 Mermaid 文件和手工审查为主。
2. **严禁可视化未实测指标**：在 [figure-assets-plan.md](file:///E:/Prelude/thesis-assets/evidence/figure-assets-plan.md) 中原计划生成的“测试数据图”、“Demo/历史真实 API 延迟对比图”等，涉及未实测性能指标的部分（如高并发压力响应、SSE高并发并发耗时等）**不得进行任何可视化画图**。仅允许对已有真实实测数据（如单用户本地/公网时延、编译构建时间等）进行绘图。
3. **图表必须先登记**：必须确保所有进入正文的图表资产均已登记在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中。
4. **图表不得替代事实**：所有图表所表达的内容必须与代码事实及实际测试数据百分之百吻合，禁止使用 nature-figure 凭空捏造未实现或未实测的图表。

## 7. P0/P1/P2 问题清单

### P0 问题（阻止进入阶段 3 的问题）
* **无。** 当前无阻止进入阶段 3 的破坏性问题。

### P1 问题（建议在阶段 3 前或阶段 3 早期处理）
* **P1-1：系统截图未完全登记。** docs/images/ 下的 7 张系统界面截图被论文正文候用，但未登记在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中。
* **P1-2：语音模块测试数据存疑。** 语音采集与 WebSocket 实时端点检测（TC-02, TC-03）目前在本地 Demo 状态下为“待实测”。正文编写时，如大篇幅提及此部分，可能面临证据链断裂。

### P2 问题（不阻塞阶段 3 准入，但限制后续写作边界）
* **P2-1：限流熔断高并发测试缺失。** Redis 拦截限流和 Resilience4j 熔断目前没有实测数据。在第五章中被严格限制，仅允许描述“实现机制”，不得出现“已通过高频流量和异常拥堵压力验证”。
* **P2-2：高并发压测指标缺失。** 无任何并发压力测试数据。阶段 3 写作第五章时，必须全面删去关于“高并发性能表现”的夸大宣称，严格限制在“单用户全栈功能连通与本地编译测试通过”。

## 8. 阶段结论

**CONDITIONAL PASS**
*(注：无 P0 级阻塞问题，但存在 P1 截图登记缺口与 P2 性能测试缺口。只要在后续阶段严格遵守本报告及测试矩阵定义的“写作边界”进行单章降噪与改写，可推进至后续阶段。)*

## 9. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 图表未刷新；
* 截图未采集；
* 引用编号未冻结；
* DOCX/PDF 未生成。
