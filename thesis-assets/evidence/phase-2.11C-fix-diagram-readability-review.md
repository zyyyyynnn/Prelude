# 阶段 2.11C-Fix 核心工程图可读性重构报告

## 1. 阶段边界

* 本阶段不是阶段 3；
* 未修改正文；
* 未采集截图；
* 未运行 nature-figure；
* 未运行 draw.io；
* 未冻结图号；
* 未冻结引用编号；
* 未生成 DOCX/PDF；
* 本阶段只重构核心工程图的可读性与导出格式。

## 2. 问题来源

在完成 `3ee48b7` 提交的事实刷新后，进行物理可读性视检时发现：
* **信息密度过高**：原图 3.2 包含 9 张表及全部 60+ 字段描述；原图 3.3 包含了所有控制器、服务实现类及底层的 SSE / rAF / 熔断限流交互，导致图形在 A4 宽度缩放后，文字和连接线极度拥挤，完全看不清。
* **高分辨率不等于可读性**：单纯在 Mermaid CLI 中提高 PNG 的输出宽度和高度（如 3000px）只能提升图片的物理分辨率，无法解决图片在插入 Word/PDF 缩放到页面宽度（约 15cm）时文字过小的物理局限。
* **重构方案**：通过“图面降维、字段外置、流式分拆、SVG 矢量化”手段，全面精简图面元素，保证图片即使缩放到 A4 正文宽度也清晰可见。

## 3. 图3.1 修复结果

| 项目 | 结果 |
| :--- | :--- |
| **源文件** | [fig-3.1-core-use-case.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd) |
| **SVG** | [fig-3.1-core-use-case.svg](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.svg) |
| **PNG** | [fig-3.1-core-use-case.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png) |
| **主要修复** | 纠正了 `求求职者 (用户)` 的拼写错误；将用户的直接连线精简至 8 个主用例；合并了配置细节和子用例；将语音交互列为规划扩展。节点总数控制在 10 个，主连线 11 条。 |
| **可读性结论** | **PASS** (在 A4 宽度下完美阅读) |

## 4. 图3.2 重构结果

* **重构动作**：将字段级 E-R 图全面降维为“核心实体关系图”。图中各实体仅保留主键（PK）、外键（FK）和最多一个业务标识字段（如 `username`、`name`、`status`、`provider_key`），隐藏所有审计时间戳、通用字段和长文本大体积字段。
* **数据外置**：省略的所有字段细节、中文注释和表逻辑定义均完整提取保存至 [database-table-dictionary-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md) 字典文件中。
* **效果**：图 3.2 节点尺寸大幅缩小，实体关系连线清晰流畅，在 A4 正文排版中完全可读。

## 5. 图3.3 重构结果

* **重构动作**：全量大图重构为“总体分层架构图”。删除并合并了 `AuthController` 等 6 个 Controller 节点为单个“路由控制层 (Controllers)”；删除合并了 `AuthServiceImpl` 等实现类为“业务服务层 (Services)”；删除了大段 LLM Provider 细分子节点。
* **数据外置**：将复杂的 SSE 接收与流式推送、rAF 刷新缓冲等技术链路细节从图 3.3 中完全移出，另立为第四章流式处理的候选流程图。
* **效果**：层级清爽，模块关系简洁，节点数量缩减为 17 个，图面比例在 Word 中极其协调。

## 6. 可选图4.x 流式响应候选图

为弥补图 3.3 移出流式细节后的技术论证空缺，本阶段新增了以下图件作为第四章《系统交互与流式设计》的备选图源：
* **源文件**：[fig-4.x-sse-streaming-flow.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.mmd)
* **SVG**：[fig-4.x-sse-streaming-flow.svg](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.svg)
* **PNG**：[fig-4.x-sse-streaming-flow.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.png)
* **主要内容**：梳理并呈现了 `/chat` 请求、SseEmitter 建立、LlmRouter 接口流式读取、SSE 增量推送、前端 ReadableStream 接收与 rAF 降频渲染（60fps）及最终的 MySQL 异步归档全流向。
* **用途声明**：使用 `fig-4.x` 占位代号，不冻结图号，不登记入 figure-table-register.md，仅作为第四章改写时的技术备件。

## 7. 导出记录

| 图 | SVG 导出 | PNG 导出 | 命令 | 结果 | 问题 |
| :--- | :---: | :---: | :--- | :---: | :--- |
| **图3.1** | 是 | 是 | `npx -y @mermaid-js/mermaid-cli -i fig-3.1-core-use-case.mmd -o ...` | 成功 | 无 |
| **图3.2** | 是 | 是 | `npx -y @mermaid-js/mermaid-cli -i fig-3.2-database-er.mmd -o ...` | 成功 | 无 |
| **图3.3** | 是 | 是 | `npx -y @mermaid-js/mermaid-cli -i fig-3.3-system-architecture.mmd -o ...` | 成功 | 无 |
| **图4.x** | 是 | 是 | `npx -y @mermaid-js/mermaid-cli -i fig-4.x-sse-streaming-flow.mmd -o ...` | 成功 | 无 |

## 8. 可读性自查

| 图 | 节点数量 | 是否适合正文 | 是否仍需 draw.io | 结论 | 备注说明 |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **图3.1** | 10 | 是 | 否 | **PASS** | 布局精简，文字大小正常 |
| **图3.2** | 9 | 是 | 否 | **PASS** | 去除了多余字段框体，结构完美 |
| **图3.3** | 17 | 是 | 可选 | **PASS** | 模块层级清晰，单节点文字不超过两行 |
| **图4.x** | 12 | 是 | 否 | **PASS** | 顺序流程一目了然 |

## 9. 禁止内容复核

本阶段严格执行了学术诚实约束，未生成或记录以下任何虚假或未实测性能指标图件：
* 未生成高并发压力测试结果图；
* 未生成 Redis / Lua 限流拦截率效果图；
* 未生成 Resilience4j 熔断备灾切换曲线图；
* 未生成 SSE 长连接并发抖动及重连稳定性测试图；
* 未生成 ASR 实时语音识别率及多设备兼容性量化图；
* 未生成 AI 评分公平性比对实验图表；
* 未生成生产环境吞吐率性能图。

## 10. P0/P1/P2 问题清单

### P0 问题（阻止推进的破坏性红线）
* **无。** 导出的 SVG 和 PNG 格式正常，缩放可读性完全通过；图中不包含任何虚构模块或未实测宣称。

### P1 问题（后续阶段需关注的对齐工作）
* **P1-1：SVG 格式在 Word 中插入排版的兼容性。** 微软 Office Word / WPS 对 SVG 支持程度不同，在阶段 3 排版合版时需人工视检是否存在格式走样。若格式出现偏移，应退避采用本阶段导出的高分辨率备份 PNG。
* **P1-2：图4.x 候选图的后续登记。** `fig-4.x` 当前作为备选，如果阶段 3 在正文中实际引用，需在 figure-table-register.md 中补登记并确定正式图号。
* **P1-3：数据库逻辑关联确认。** E-R 图绘制的逻辑关系需在后续阶段由用户做终审确认。

### P2 问题（后续美化空间）
* **P2-1：draw.io 排版微调。** 架构图可读性虽通过，但后续在答辩 PPT 中展示时，可使用 draw.io 导入图源进行背景配色和更精致的对齐。
* **P2-2：Mermaid CLI 版本依赖。**

## 11. 阶段结论

**PASS**
*(注：三张核心工程图的可读性重构圆满完成，主图件 SVG 与备份 PNG 已成功导出，同时建立了数据外置字典 database-table-dictionary-2026-06.md 并新增了 sse 流式响应流程图作为第四章候选，可进入阶段 2.11D：界面截图真实采集。)*

## 12. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 截图未采集；
* 图号未冻结；
* 引用编号未冻结；
* DOCX/PDF 未生成。
