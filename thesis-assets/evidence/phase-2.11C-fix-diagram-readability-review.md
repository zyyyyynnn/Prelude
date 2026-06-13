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
| **主要修复** | 纠正了主用例角色名称的拼写重复错误（去除了多余的“求”字）；将用例图重构为 TB 垂直布局，构建了“注册/登录 -> 上传解析 -> 岗位匹配 -> 模拟面试 -> 报告反馈 -> 能力分析”的核心流程主链，用户仅单点连接到起点与两个旁路分支（演示环境和语音扩展），消除了星型连线交叉。节点数 10，主连线 11 条。 |
| **可读性结论** | **PASS** (在 A4 宽度下完美阅读) |

## 4. 图3.2 重构结果

* **重构动作**：将字段级 E-R 图全面降维为“核心实体关系图”。图中各实体仅保留主键（PK）、外键（FK）和最多一个业务标识字段（如 `username`、`name`、`status`、`provider_key`），隐藏所有审计时间戳、通用字段和长文本大体积字段。同时将 `user` 到 `score_history` 和 `user_weakness` 的冗余连线删除，聚焦以会话为中心的逻辑链条，消除长线绕行。
* **数据外置**：省略的所有字段细节、中文注释和表逻辑定义均完整提取保存至 [database-table-dictionary-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md) 字典文件中。
* **效果**：图 3.2 节点尺寸大幅缩小，实体关系连线清晰流畅，在 A4 正文排版中完全可读。

## 5. 图3.3 重构结果

* **重构动作**：将原图 3.3 包含的 6 个 subgraph 细化容器全部打散，架构图由垂直布局改为横向 `flowchart LR` 拓扑。精简为“前端展现层 -> 安全控制网关 -> 应用层 -> 核心业务 -> 数据中间件/模型服务”的单线五级分层链条。
* **数据外置**：将复杂的 SSE 接收与流式推送、rAF 刷新缓冲等技术链路细节从图 3.3 中完全移出，另立为第四章流式处理的候选流程图。
* **效果**：没有复杂的子图容器干扰，节点文字不超过两行，横向比例极其协调，在 Word 中不显松散。

## 6. 可选图4.x 流式响应候选图

为弥补图 3.3 移出流式细节后的技术论证空缺，本阶段新增了以下图件作为第四章《系统交互与流式设计》的备选图源：
* **源文件**：[fig-4.x-sse-streaming-flow.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.mmd)
* **SVG**：[fig-4.x-sse-streaming-flow.svg](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.svg)
* **PNG**：[fig-4.x-sse-streaming-flow.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-4.x-sse-streaming-flow.png)
* **主要内容**：将原有的 12 步线性长链重构为“前端 / 后端 / 外部模型”的三泳道流程图。明确了页面发送与 rAF 刷新缓冲渲染在前端泳道，SseEmitter 建立、路由请求与异步保存逻辑在后端泳道，流式 Chunk 吐出与结束符校验在模型提供商泳道。
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
| **图3.1** | 10 | 是 | 否 | **PASS** | TB 布局，线段完全不交叉，流程清晰 |
| **图3.2** | 9 | 是 | 否 | **PASS** | 关系集中在会话上，去除了 user 直接连线的绕行 |
| **图3.3** | 8 | 是 | 否 | **PASS** | 横向五级连线，文字清晰，非常紧凑 |
| **图4.x** | 11 | 是 | 否 | **PASS** | 三泳道流程，泳道框极窄，线条横纵分明 |

## 9. 禁止内容复核

本阶段严格执行了学术诚实约束，未生成或记录以下任何虚假或未实测性能指标图件：
* 未生成高并发压力测试结果图；
* 未生成 Redis / Lua 限流拦截率效果图；
* 未生成 Resilience4j 熔断备灾切换曲线图；
* 未生成 SSE 长连接并发抖动及重连稳定性测试图；
* 未生成 ASR 实时语音识别率及多设备兼容性量化图；
* 未生成 AI 评分公平性比对实验图表；
* 未生成招聘效果优于人工图；
* 未生成生产环境性能图。

## 10. P0/P1/P2 问题清单

### P0 问题（阻止推进的破坏性红线）
* **无。** 导出的 SVG 和 PNG 格式正常，缩放可读性完全通过；图中不包含任何虚构模块或未实测宣称。

### P1 问题（需要用户确认或后续阶段完善的问题）
* **P1-1：SVG 格式在 Word 中插入排版的兼容性。** 微软 Office Word / WPS 对 SVG 支持程度不同，在阶段 3 排版合版时需人工视检是否存在格式走样。若格式出现偏移，应退避采用本阶段导出的高分辨率备份 PNG。
* **P1-2：图4.x 候选图的后续登记。** `fig-4.x` 当前作为备选，如果阶段 3 在正文中实际引用，需在 figure-table-register.md 中补登记并确定正式图号。
* **P1-3：数据库逻辑关联确认。** E-R 图精简后隐藏了 user 的多路直接关联，该设计需在后续阶段由用户做终审确认。

### P2 问题（后续美化空间）
* **P2-1：draw.io 排版微调。** 架构图可读性虽通过，但后续在答辩 PPT 中展示时，可使用 draw.io 导入图源进行背景配置。
* **P2-2：Mermaid CLI 版本依赖。**

## 11. 阶段结论

**PASS**
*(注：核心工程图的视觉排版与线条协调重构圆满完成，SVG 与 PNG 图件导出成功且完全去除了长线绕路与容器膨胀，可读性极佳，允许进入阶段 2.11D：界面截图真实采集。)*

## 12. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 截图未采集；
* 图号未冻结；
* 引用编号未冻结；
* DOCX/PDF 未生成。
