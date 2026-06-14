# 阶段 2.11C 核心工程图事实刷新报告

> **[后续口径说明]**
> 本文保留历史阶段审查语境。2026-06-13 后，RabbitMQ 已接入代码层并完成本地 Docker Compose 基础链路联调；当前报告任务队列事实以 `impl-2026-06-13-rabbitmq.md` 与 `final-evidence-lock.md` 为准。

## 1. 阶段边界

* 本阶段不是阶段 3；
* 未修改正文；
* 未采集截图；
* 未运行 nature-figure；
* 未运行 draw.io；
* 未冻结图号；
* 未冻结引用编号；
* 未生成 DOCX/PDF；
* 只刷新三张核心工程图的 Mermaid 图源和可选 PNG 导出。

## 2. 审查输入

本报告生成前读取并审查了以下文件与配置代码：
1. 治理与规划文件：[workflow-governance.md](file:///E:/Prelude/thesis-assets/meta/workflow-governance.md)、[final-evidence-lock.md](file:///E:/Prelude/thesis-assets/meta/final-evidence-lock.md)、[figure-assets-plan.md](file:///E:/Prelude/thesis-assets/evidence/figure-assets-plan.md)、[figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md)；
2. 项目后端结构与代码：[pom.xml](file:///E:/Prelude/backend/pom.xml)、[InterviewBackendApplication.java](file:///E:/Prelude/backend/src/main/java/com/interview/InterviewBackendApplication.java)；
3. 后端服务与路由控制器：[AuthController.java](file:///E:/Prelude/backend/src/main/java/com/interview/controller/AuthController.java)、[ResumeController.java](file:///E:/Prelude/backend/src/main/java/com/interview/controller/ResumeController.java)、[InterviewController.java](file:///E:/Prelude/backend/src/main/java/com/interview/controller/InterviewController.java)、[AnalyticsController.java](file:///E:/Prelude/backend/src/main/java/com/interview/controller/AnalyticsController.java)、[LlmController.java](file:///E:/Prelude/backend/src/main/java/com/interview/controller/LlmController.java)；
4. 核心 Service 实现：[AuthServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/AuthServiceImpl.java)、[ResumeServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/ResumeServiceImpl.java)、[InterviewServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java)、[UserLlmConfigServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/UserLlmConfigServiceImpl.java)、[LlmRouter.java](file:///E:/Prelude/backend/src/main/java/com/interview/llm/LlmRouter.java)、[VoiceServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/VoiceServiceImpl.java)、[SessionRagServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/SessionRagServiceImpl.java)；
5. 数据库 Entity 与 schema 脚本：[schema.sql](file:///E:/Prelude/backend/src/main/resources/schema.sql)、`backend/src/main/java/com/interview/entity/` 下的各个 Entity 类；
6. 前端配置文件与代码：[package.json](file:///E:/Prelude/frontend/package.json)、[playwright.demo.config.ts](file:///E:/Prelude/frontend/playwright.demo.config.ts)、[InterviewView.vue](file:///E:/Prelude/frontend/src/views/InterviewView.vue)。

## 3. 当前代码事实摘要

| 模块 | 代码或配置来源 | 是否进入图表 | 理由 | 风险说明 |
| :--- | :--- | :---: | :--- | :--- |
| **用户认证** | `AuthController` 与 `AuthServiceImpl` (BCrypt / JWT) | 是 | 系统基础服务，真实用例与路由支撑。 | 无风险。 |
| **简历解析** | `ResumeController` 与 `ResumeServiceImpl` (PDFBox) | 是 | 系统核心功能用例，数据库中存在 resume 表关联。 | 无风险。 |
| **岗位匹配** | `PositionController` 与 `PositionServiceImpl` | 是 | 岗位模板与简历匹配，数据库中存在 position_template 表关联。 | 无风险。 |
| **模拟面试状态机** | `InterviewServiceImpl` 四阶段面试状态推进 | 是 | 状态机流转支持，数据库中有 interview_stage 关联表。 | 无风险。 |
| **SSE / 流式响应** | `InterviewController.chat` 与 `SseEmitterRegistry` | 是 | 会话消息通过 SSE 逐字流式分发至前端，是第四章主要技术点。 | 仅能作为流向表达，不暗示在高负载下进行过并发测试。 |
| **面试报告** | `InterviewReportParser` (Structured Output JSON 评分抽取) | 是 | 报告解析逻辑，数据库中包含 score_history 与 user_weakness 实体。 | 需明确大模型提取的结构化结论不等同于客观考官，属辅助手段。 |
| **LLM 网关** | `LlmRouter` 适配 DeepSeek, OpenAI 和 Anthropic | 是 | 抽象路由层与解密模块，提供用户自定义多渠道模型配置。 | 需说明 API 调用稳定性受公网影响。 |
| **PDFBox** | `ResumeServiceImpl` 中依赖 `pdfbox-3.0.3` | 是 | 简历解析本地文本抽取核心库。 | 仅限普通 PDF 字符抽取，不包含复杂图文 OCR。 |
| **MySQL** | `schema.sql` 包含 9 张表结构定义 | 是 | 系统唯一真相持久化介质。 | 无风险。 |
| **Redis** | `LlmRateLimitInterceptor` 限流连接 Redis | 是 | 滑动窗口高频请求拦截，以及流闪断状态对齐。 | 无压测数据，仅作为拦截与对齐机制写入。 |
| **RabbitMQ** | `rabbitmq` 依赖及异步队列逻辑 | 是 | 用于处理报告异步生成和并发任务解耦。 | 仅属于代码设计与异步解耦机制，不体现高并发压测结果。 |
| **Resilience4j** | `application-local.yml` 熔断机制配置 | 是 | 作为 LlmRouter 调用的故障备灾切换机制组件。 | 无压测数据，仅作为代码机制写入。 |
| **语音 / ASR** | `VoiceServiceImpl` 与 WebSocket 处理器 | 是 (标规划) | 音频 WebSocket 处理代码已编写，但目前无公网 ASR 真实对接。 | **极高风险。在架构图和用例图中必须显式标注为“规划 / 待实测”或虚线隔离，防止夸大。** |
| **高并发 / 压测** | 无 | 否 | 项目中完全缺失压测命令、脚本与结果文件。 | 绝对禁止画入架构图或作为已通过指标。 |
| **企业招聘闭环** | 无 | 否 | 系统属于求职者模拟训练工具，无企业 HR 招聘决策逻辑及第三方对接代码。 | 绝不得画入用例图或架构图。 |

## 4. 图3.1 刷新结果

| 项目 | 结果 |
| :--- | :--- |
| **源文件** | [fig-3.1-core-use-case.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd) |
| **输出文件** | [fig-3.1-core-use-case.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png) |
| **主要变更** | 重构角色为“求职者”和“LLM 服务”；加入“设置个人信息 & 加密 LLM 配置”、“启动/重置 Demo Twin 隔离环境”等真实用例；将语音交互标注为 `(规划/待实测)` 并采用虚线框独立标记。 |
| **事实来源** | 后端 `UserController`, `LlmController`, `DemoController` 的 REST 接口定义，以及 Vue 前端实际路由。 |
| **排除内容** | 排除“企业 HR”、“招聘决策评估”、“高并发流量监控”等非真实代码支持的虚构用例。 |
| **当前状态** | **PASS** |

## 5. 图3.2 刷新结果

| 项目 | 结果 |
| :--- | :--- |
| **源文件** | [fig-3.2-database-er.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.mmd) |
| **输出文件** | [fig-3.2-database-er.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.png) |
| **主要变更** | 将实体名和表名全部修改为与 `schema.sql` 严格一致的英文命名；完整补齐了所有实体属性字段与注释；新增了 `llm_provider_config` 实体表；添加了 `user` 到 `score_history` 和 `user_weakness` 的逻辑外键关联线。 |
| **事实来源** | [schema.sql](file:///E:/Prelude/backend/src/main/resources/schema.sql) 以及对应的 Java JPA Entity 类。 |
| **排除内容** | 排除任何在当前数据库中未定义的虚构表，核心字段以外键和 UK 为最高匹配优先级。 |
| **当前状态** | **PASS** |

## 6. 图3.3 刷新结果

| 项目 | 结果 |
| :--- | :--- |
| **源文件** | [fig-3.3-system-architecture.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd) |
| **输出文件** | [fig-3.3-system-architecture.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png) |
| **主要变更** | 重构了前端用户层、拦截层、控制器层、业务逻辑层和数据中间件层；加入了 `LlmRateLimitInterceptor` 限流、`Resilience4j` 熔断、`ThreadPoolConfig` 线程池和 `RabbitMQ` 异步组件；详细勾勒并标注了模型流式输出通过 `SseEmitter` 增量推送至前端 `ReadableStream` 并执行 `rAF` 节流缓冲渲染的 SSE 流式响应闭环链路。 |
| **事实来源** | 后端项目物理文件、配置文件以及 [InterviewServiceImpl.java](file:///E:/Prelude/backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java) / [InterviewView.vue](file:///E:/Prelude/frontend/src/views/InterviewView.vue) 的 SSE 通信和渲染机制。 |
| **排除内容** | 严格排除 Kubernetes 集群、云端 HA 部署、大规模负载压力测试以及 HR 企业对接等虚构服务。 |
| **当前状态** | **PASS** |

## 7. Mermaid 导出记录

| 图 | 导出命令 | 是否成功 | 输出文件 | 问题 |
| :--- | :--- | :---: | :--- | :--- |
| **图3.1** | `npx -y @mermaid-js/mermaid-cli -i fig-3.1-core-use-case.mmd -o fig-3.1-core-use-case.png` | 是 | [fig-3.1-core-use-case.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png) | 无。导出成功。 |
| **图3.2** | `npx -y @mermaid-js/mermaid-cli -i fig-3.2-database-er.mmd -o fig-3.2-database-er.png` | 是 | [fig-3.2-database-er.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.png) | 无。导出成功。 |
| **图3.3** | `npx -y @mermaid-js/mermaid-cli -i fig-3.3-system-architecture.mmd -o fig-3.3-system-architecture.png` | 是 | [fig-3.3-system-architecture.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png) | 无。导出成功。 |

## 8. 禁止写入正文的图表能力

本阶段及后续写作阶段中，以下未实测或虚构的能力**绝对禁止生成任何图表**或在正文中作为测试通过展现：
* 本阶段未产生高并发压测图（无 JMeter 或 LoadRunner 并发压力统计结果）；
* 未产生限流熔断效果图（限流和熔断仅通过代码和配置表示原理，无真实流量拦截阻断曲线）；
* 未产生 SSE 并发稳定性图（无并发长连接闪断与丢包率监测图）；
* 未产生 ASR 语音识别准确率图（实时语音 ASR 模块处于待实测规划中，无相关语料准确率量化图）；
* 未产生 AI 评分公平性图（无算法公平性量化比对）；
* 未产生招聘效果优于人工图（无真实招聘转化对比实验）；
* 未产生生产环境性能图（所有实测性能均是在本地单机及开发回环下获取）。

## 9. P0/P1/P2 问题清单

### P0 问题（阻止推进的破坏性红线）
* **无。** 三张 `.mmd` 语法经由 Mermaid CLI 校验全部解析成功，导出了 PNG 图件；图中仅包含当前源码存在的实际模块与合理规划，不包含任何浮夸夸大宣称。

### P1 问题（需要用户确认或后续阶段完善的问题）
* **P1-1：前端页面在用例图中的体现。** 前端主要的 `InterviewView`, `ResumesView`, `AnalyticsView`, `LoginView` 以及配置弹框均已在图3.1的核心用例中建立合理映射，若后续路由发生深度变更，需与用例图保持同步。
* **P1-2：数据库 Entity 关联细化确认。** 评分历史和薄弱点关联在 MySQL 中存在显式的外键约束，但在实际 MyBatis-Plus 中采用逻辑外键，ER 图中已正确绘制该逻辑关系，需要用户确认无误。

### P2 问题（后续美化与优化空间）
* **P2-1：图形美观度微调。** 核心工程图通过 Mermaid 默认布局生成，视觉排布偏于标准。在阶段 3 排版及答辩 PPT 制作时，可使用 draw.io 导入图源进行精细的人工排版微调。
* **P2-2：Mermaid CLI 版本未全局锁定。** 本地通过 `npx` 临时下载并执行了 `11.15.0` 版本的 Mermaid CLI。若后续需要保证自动化构建环境一致性，建议在前端或根目录的 package.json 中显式锁死 `@mermaid-js/mermaid-cli` 的版本依赖。

## 10. 阶段结论

**PASS**
*(注：三张核心工程图已完成事实刷新， Mermaid 语法编译通过，PNG 文件导出成功且无一处夸大宣称，完全符合论文准入计划约束，允许进入阶段 2.11D：界面截图真实采集。)*

## 11. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 截图未采集；
* 图号未冻结；
* 引用编号未冻结；
* DOCX/PDF 未生成。

## 附：2.11C-Fix 可读性重构记录

* **重构背景**：在 3ee48b7 的事实核对完成后，发现图3.2与图3.3的 PNG 缩放后字体过密、在 A4 正文宽度下严重不可读。为此，本阶段执行了 2.11C-Fix 优化重构。
* **重构方案**：简化图源以降低信息密度；以外部 `database-table-dictionary-2026-06.md` 形式保存字段描述以简化 E-R 图；从架构图中移出 SSE 流式底层细节并拆分为第四章流式响应候选流程图；导出 SVG 作为主图件，高分辨率 PNG 作为备份。
* **准入依据**：以 2.11C-Fix 报告（phase-reports/phase-2.11C-fix-diagram-readability-review.md）为图件可读性准入的最新评估依据，原 2.11C 事实核对结论仍保留效力。
