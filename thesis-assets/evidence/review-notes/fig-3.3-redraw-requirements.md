# 图 3.3 系统架构图重绘需求说明

> **[SUPERSEDED / PARTIALLY COMPLETED]**
> 本需求已由 `thesis-assets/evidence/phase-reports/phase-2.11C-diagram-refresh-report.md` 与
> `thesis-assets/evidence/phase-reports/phase-2.11C-fix-diagram-readability-review.md` 部分承接。
> 图 3.3 已完成业务架构精简与可读性重构；高可用、监控、限流熔断等细节不得混入业务架构图。
> 2026-06-13 后，RabbitMQ 已接入代码层并完成本地 Docker Compose 基础链路联调；当前报告任务队列事实以 `rabbitmq-report-queue-2026-06-13.md` 与 `final-evidence-lock.md` 为准。图 3.3 是否加入 RabbitMQ 仍需由用户和审查官统一决定。

## 0. 结论

结论：拆分

原因：当前 2026-06 最新代码事实中，系统已演进出完整的稳定性层（Redis 限流、Resilience4j 熔断）与监控层（Prometheus/Grafana）。若强行塞入原图 3.3，会导致连线交叉混乱、节点极度拥挤。建议拆分为两张图：
- 图 3.3：系统整体业务架构图；
- 新增高可用与监控链路图：暂称“候选图 3.4”，最终编号需在正文和图表登记阶段由用户与审查官确认。

## 1. 当前图 3.3 状态

| 项 | 当前状态 | 证据 |
| --- | --- | --- |
| 前端节点 | 仅展示 "Vue 3 / SSE" | `fig-3.3-system-architecture.mmd` |
| 后端节点 | 仅展示 "Spring Boot API" | 同上 |
| 通信链路 | 仅展示单向 HTTP 与 SSE | 同上 |
| 数据/外部节点 | 仅展示 MySQL、PDFBox、LLM、Demo | 同上 |
| 缺失项 | 缺失 Redis、监控、熔断、WebSocket、双生隔离机制等 | 对比 `structured-output-resilience-2026-06-02.md` 与工程源码 |

## 2. 必须进入图 3.3 的节点

| 层级 | 节点 | 是否入图 | 原因 | 证据文件 |
| --- | --- | --- | --- | --- |
| 前端层 | Vue 3, Vite, Pinia, ECharts | 是 | 核心工程基础，承载雷达图报告与状态管理 | `frontend/package.json` |
| 通信层 | REST API, SSE, WebSocket | 是 | 核心通信矩阵，不可或缺 | `functional-cases-2026-06.md` |
| 后端层 | Spring Boot API, 面试编排, 报告解析 | 是 | 业务调度中心，增加解析模块 | `structured-output-resilience-2026-06-02.md` |
| LLM Provider层 | 多供应商模型池 (DeepSeek/OpenAI/Claude) | 是 | 核心心智计算，且支持多厂商切换 | README.md / 项目能力描述 |
| 数据处理层 | MySQL, PDFBox, Redis | 是 | Redis 在图 3.3 中可作为“会话状态 / 缓存 / Demo 状态隔离”节点 | `docker-compose.yml`, `pom.xml` |
| 隔离演练层 | Demo Twin 影子沙盒 | 是 | 本系统核心卖点，需明确隔离边界 | `docs/demo.md`, `start-demo.bat` |

## 3. 必须进入图 3.3 的链路

| 链路 | 是否入图 | 原因 | 证据文件 |
| --- | --- | --- | --- |
| 前端 -> 后端 WebSocket (拾音) | 是 | 支持 Voice 实时传输 | `functional-cases-2026-06.md` |
| 后端 -> 前端 SSE (流式合成) | 是 | 支持低延迟字幕与发音下发，包含重连退避 | `structured-output-resilience-2026-06-02.md` |
| 后端 -> LLM Provider | 是 | 大模型调用交互，支持降级 | `security-performance-hardening-2026-05-31.md` |

## 4. 不应进入图 3.3 的内容

| 内容 | 不入图原因 | 后续处理 |
| --- | --- | --- |
| RabbitMQ | 已接入代码层，但当前图 3.3 暂未加入 RabbitMQ 节点。 | 待第四章正文小节稳定后，再由用户和审查官决定是否入图。 |
| Prometheus & Grafana | 属于外挂监控设施，若混入业务图会造成结构失衡。 | 建议转移至候选高可用与监控链路图。 |
| Resilience4j 熔断与 Lua 限流 | 属于防御性非功能链路。 | Redis/Lua 限流应放在候选高可用与监控链路图中，不与业务缓存角色混写。 |
| JWT 与 AES 加密 | 属于代码内部细节级拦截策略，非高层宏观节点。 | 在第四章正文中文字叙述，不强行入架构大图。 |

## 5. 建议图表结构

| 方案 | 是否推荐 | 原因 |
| --- | --- | --- |
| B. 拆成两张图 | 是 | 系统复杂度已超出单图承载极限。图 3.3 专注业务流（面试、报告、Demo 隔离、多模态通信），新增的候选高可用与监控链路图专注稳定性底座（监控、熔断、限流）。 |
| A. 一张完整架构图 | 否 | 将导致图表可读性剧烈下降。 |

## 6. 推荐工具

| 工具 | 是否推荐 | 用途 | 禁止事项 |
| --- | --- | --- | --- |
| Mermaid | 是 | 图 3.3 及可能新增的图 3.4 的基准源码 | 必须保持代码级精确对应，不得随意发散。 |
| draw.io | 是 | 用于后续学校论文的最终 Word 排版微调 | 初期禁止绕开 MMD 直接作画。 |
| nature-figure | 当前阶段不使用 | 当前阶段不使用；后续仅可作为可选视觉重绘/导出工具。 | 输入必须来自已确认的 Mermaid 源、节点表和链路表。不得替代架构事实判断，不得自动生成脱离源码的节点。 |
| PaperSpine | 否 | 此为正文处理引擎。 | 本阶段与图表重绘阶段完全禁用。 |

## 7. 后续执行边界

写明：
- 本文件只锁定需求，不重画图。
- 后续如重画，应只修改 evidence/diagrams/fig-3.3-system-architecture.mmd 及导出的 PNG/SVG。
- 后续如拆分新增候选图 3.4，必须先得到用户和审查官确认，并在图表登记表中注册。
- 不得直接修改正文引用编号。
