
## 项目漂移闸门

后续项目仍会持续开发，可能引入新的技术栈、配置、中间件、服务链路、UI 路由、测试方式或部署模式。

当项目本体发生实质变化时，禁止直接修改论文正文。必须先执行项目漂移审查，判断项目本体是否已经领先论文资产。

任何以下变化都必须触发漂移审查：
1. 后端架构变化。
2. 新增或删除中间件。
3. 新增 Redis / RabbitMQ / WebSocket / SSE / Resilience4j / Prometheus / Grafana 等链路。
4. LLM Provider、模型配置、API Key 管理、降级策略变化。
5. Demo Twin、demo profile、reset-demo、演示端口或隔离数据库变化。
6. 数据库表结构、实体类、DDL、Mapper 或迁移脚本变化。
7. 前端页面、路由、状态管理、图表组件、语音组件变化。
8. README 宣称能力变化。
9. 测试环境、构建工具、依赖版本、测试数据变化。
10. 答辩演示路径变化。

如果项目本体领先论文资产较多，必须暂停正文改写，先补齐：
- evidence/
- figure-table-register.md
- 图表需求说明
- 测试数据
- final-evidence-lock.md
- reference-refresh 影响判断

| 漂移类型 | 是否触发审查 | 影响资产 | 禁止直接操作 |
| --- | --- | --- | --- |
| 架构与链路 | 是 | evidence/、架构图 | 直接修改正文 |
| 数据库表 | 是 | evidence/、E-R图 | 直接修改正文 |
| 业务逻辑 | 是 | evidence/、用例图 | 直接修改正文 |
| 测试与演示 | 是 | evidence/、测试记录 | 直接修改正文 |

## 双 Deep Research 报告规则

本论文后续必须形成两份本地研究报告资产：

1. GPT Deep Research 报告：
   thesis-assets/literature/research-notes/gpt-deep-research-2026-06.md

2. Gemini Deep Research 报告：
   thesis-assets/literature/research-notes/gemini-deep-research-2026-06.md

两份报告均要求：
- 中文为主；
- 保留英文文献原题；
- 保留 DOI、arXiv ID、出版社页、期刊页或官方链接；
- 不直接进入 chapters/*.md；
- 不直接改正文引用编号；
- 不直接覆盖 quality-review.md；
- 必须先进入 reference-refresh-plan.md 所定义的文献翻新流程；
- 必须经过人工核验后，才允许进入正式参考文献候选池。

GPT Deep Research：
LLM 驱动的模拟面试、简历诊断、教育训练系统研究综述。

Gemini Deep Research：
AI interview training、resume-job matching、LLM education assistant 的交叉验证报告。

## 参考文献翻新规则

参考文献必须执行一轮独立翻新，不允许直接沿用旧文献表进入最终论文。

翻新目标：
- 正式参考文献总数：25±5 条；
- 中文文献：10-14 条；
- 英文文献：10-14 条；
- 官方技术文档：4-6 条；
- 官方技术文档只进入第二章、第三章、第四章等技术依据位置，不得挤占第一章/第二章研究现状核心文献位置。

质量规则：
- A / A- / B+ 优先进入正式参考文献；
- B- 仅在高度贴题且无更好替代时保留；
- C 类不得进入正式参考文献；
- 元数据不完整、无法核验 DOI/期刊页/出版社页/作者年份的文献不得进入正式参考文献；
- 大模型生成的文献条目必须人工核验，未核验不得引用。

阶段 2.9：文献检索、质量筛选与参考文献翻新

阶段 2.9 不允许直接修改正文，只能产出：
thesis-assets/literature/reference-refresh-2026-06.md
thesis-assets/literature/candidates/
thesis-assets/literature/research-notes/
更新后的 quality-review.md 草案
更新后的 evidence-map.md 草案

## 论文 skills 主动参与规则

论文相关 skills 必须在可控边界内主动参与论文资产生产，不得长期闲置。

PaperSpine 使用边界：
- paper-spine-research：参与文献候选整理，但不得替代 GPT/Gemini 两份 Deep Research 报告。
- paper-spine-citation：参与引用格式、引用质量、元数据完整性审查，但不得直接改正文编号。
- paper-spine-rewrite：只在证据、图表、文献候选冻结后，按单章执行。
- paper-spine-audit：每章 rewrite 后必须执行。
- paper-spine-build：当前禁用，因为本论文已有正文唯一真相源。
- paper-spine-latex：当前禁用，因为本项目不走 LaTeX/PDF 自动链路。

nature-skills 使用边界：
- 当前已确认 nature-figure 可用。
- nature-figure 必须用于合适的数据图表、科研图表、可视化资产增强。
- 系统架构事实仍由代码、DDL、接口时序、Mermaid/draw.io、人审决定。
- nature-figure 可在事实图源锁定后参与视觉规范化或数据图表生成。
- nature-figure 不得绘制营销图、概念海报、脱离事实的架构图。
- nature-citation / nature-academic-search 后续允许安装和评估，但不得直接进入正文链路。
- nature-polishing 即使安装，也不能作为中文本科工程论文默认润色器。

## school-template.docx 定位规则

school-template.docx 只作为学校格式、样式、页边距、标题层级、目录、页眉页脚等格式参考。

即使 school-template.docx 当前包含全量论文叙述，也不得作为正文事实来源。

论文正文事实来源只能是：
1. chapters/*.md；
2. evidence/；
3. literature/；
4. 已核验的 research-notes/；
5. 已审核的 reference-refresh 结果。

任何论文文字叙述改写，只允许修改 chapters/*.md。
不得从 school-template.docx 反向同步正文。
不得以 current/thesis-final.docx 或 school-template.docx 作为正文修改源。

