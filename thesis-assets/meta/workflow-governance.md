# Prelude 论文工作流治理规范

## 1. 文件定位与规范优先级

本文件是 Prelude 论文资产治理的最高规范。
任何 Agent 在修改论文资产前，必须先读取本文件。
若本文件与其他论文流程文件冲突，以本文件为准。

规范优先级：
workflow-governance.md > AGENTS.md > thesis-assets/README.md > thesis-assets/build-docx.ps1

## 权威源声明

- official/gzu-format-requirements.md 是格式要求整理稿。
- official/gzu-thesis-work-guide.doc 是学校工作指南原件。
- meta/gzu-thesis-template.docx 是唯一 active Word 格式模板。
- chapters/*.md 是正文唯一真相源。

## 2. 决策权归属

1. 本地 Agent 无权自行判定阶段通过。
2. 本地 Agent 无权自行推进下一阶段。
3. 本地 Agent 只能执行用户明确指定的当前阶段任务。
4. 每一阶段完成后，必须输出报告与 git diff，由用户和审查官复核。
5. 未经用户和审查官复核确认，不得进入下一阶段。
6. 本地 Agent 的“通过”只能视为自检结果，不等同于项目验收结论。

## 3. 核心红线

1. chapters/*.md 是论文正文唯一真相源。
2. thesis-assets/current/ 仅允许由构建脚本在后续阶段临时生成，不作为当前手工维护资产。
3. thesis-working-draft.docx 若被生成，仅是自动构建工作稿，不是提交版。
4. 提交版 DOCX/PDF 只能在 Word/WPS 人工终审后产生。
5. 证据未锁定，不允许调起 paper-spine-rewrite。
6. 外部研究未进入 literature/ 或 evidence/ 的研究缓冲区，不允许进入正文。
7. 图表未登记，不允许在正文中引用。
8. PaperSpine 只能单章执行。
9. 严禁全文一次性送入 PaperSpine。
10. Deep Research / NotebookLM 输出不得直接覆盖 chapters/*.md。
11. nature-figure 不得脱离真实代码、DDL、接口时序、测试数据自行想象图表。
12. 禁止使用“查重反制”“规避检测”等目标或表述。

## 4. 四个冻结点

| 冻结点 | 含义 | 冻结前禁止 | 冻结后允许 |
| --- | --- | --- | --- |
| 证据锁定 | evidence/、图表登记、测试数据、代码片段已确认。 | 禁止 PaperSpine rewrite。 | 允许单章改写或降噪。 |
| 内容冻结 | chapters/*.md 正文不再大改。 | 禁止生成提交版 DOCX/PDF。 | 允许生成 DOCX 工作稿。 |
| 引用冻结 | 文献编号、参考文献列表、引用位置已对齐。 | 禁止最终排版。 | 允许人工 Word 终审。 |
| 提交冻结 | Word/PDF 人工检查完成。 | 禁止继续自动化修改。 | 允许归档、tag、提交。 |

## 5. 阶段准入、退出、熔断规则

| 阶段 | Entry Criteria | Exit Criteria | Stop Conditions |
| --- | --- | --- | --- |
| 阶段 1.6：治理规范落盘 | 用户指派阶段 1.6 任务 | 规范文件与入口修正完成 | 发现破坏章节/图表 |
| 阶段 2：图表和证据同步 | 阶段 1.6 审核通过 | evidence/ 证据已锁定登记 | 证据无法溯源到代码 |
| 阶段 3：正文单章学术降噪 | 证据已锁定 | 单章无异常修辞、事实不漂移 | 单章上下文截断 |
| 阶段 4：引用体系治理 | 内容已冻结 | 正文编号与列表对应 | 编号跳跃缺失 |
| 阶段 5：DOCX 工作稿构建 | 引用已冻结 | 生成 thesis-working-draft.docx 工作稿 | Pandoc 报错中断 |
| 阶段 6：Word/PDF 人工终审 | DOCX 工作稿已生成 | 人工导出提交版 PDF 并锁定 | 发现排版乱码 |

任何阶段一旦发现 P0，立即停止进入下一阶段。
任何阶段完成后，必须等待用户和审查官复核。

## 6. 项目更新与论文同步规则

| 变更类型 | 是否必须同步论文 | 影响章节 | 需要更新的资产 | 是否需要更新图表 | 是否需要重跑测试 |
| --- | --- | --- | --- | --- | --- |
| 后端架构变化 | 是 | 第三章、第四章 | 代码片段 | 是（架构图） | 视性能影响决定 |
| 数据库结构变化 | 是 | 第三章 | 无 | 是（E-R 图） | 否 |
| 核心 API 语义变化 | 是 | 第三/四/五章 | 代码片段 | 否 | 视测试覆盖决定 |
| LLM Provider / SSE / WebSocket / Redis / 熔断 / 监控变化 | 是 | 第四章和第五章 | 代码片段 | 否 | 必要时重跑测试 |
| Demo Twin 机制变化 | 是 | 第四章、第五章、答辩材料 | 测试数据 | 否 | 必要时重采 Demo 数据 |
| README 宣称能力变化 | 必须核对 | 若涉及能力变更 | 对应证据 | 否 | 否 |
| 前端页面/路由变化 | 视引用情况 | 第四章（若引用截图） | 运行截图 | 否 | 否 |
| 依赖版本变化 | 视影响决定 | 第五章 | 描述或测试环境说明 | 否 | 否 |
| 样式微调 | 否（仅记录） | 无 | 无 | 否 | 否 |
| 答辩演示路径变化 | 是 | 答辩材料、或可能正文 | 答辩文件 | 否 | 否 |

补充说明：
1. 后端架构变化：必须同步；影响第三章、第四章；可能需要架构图；视性能影响决定是否重跑测试。
2. 数据库结构变化：必须同步；影响第三章；必须更新 E-R 图。
3. 核心 API 语义变化：必须同步；影响第三/四/五章；视测试覆盖决定是否重跑测试。
4. LLM Provider / SSE / WebSocket / Redis / 熔断 / 监控变化：必须同步；影响第四章和第五章；必要时重跑测试。
5. Demo Twin 机制变化：必须同步；影响第四章、第五章、答辩材料；必要时重采 Demo 数据。
6. README 宣称能力变化：必须同步核对；如果论文已引用该能力，必须更新正文或降级 README 表述。
7. 前端页面/路由变化：如果论文正文、截图、图表、答辩演示路径引用该页面，则必须同步；否则只记录。
8. 依赖版本变化：如果影响第五章环境、构建性能、测试结论或论文中版本表述，则必须同步；否则只记录。
9. 样式微调：一般只记录，不触发论文重写。
10. 答辩演示路径变化：必须同步 defense/，如论文正文引用演示流程，也必须同步论文。

## 7. evidence/ 证据层职责

evidence/ 是正文之前的证据缓冲区。
证据未落盘，不允许正文重写。
证据未登记，不允许进入正文。
证据必须能回溯到代码、命令、截图、测试报告或文献来源。

| evidence 类型 | 存放位置 | 来源 | 进入正文条件 | 验收方式 |
| --- | --- | --- | --- | --- |
| 代码片段 | evidence/code-snippets/ | 项目源码提取 | 剥离业务仅留核心说明 | 源码一致性核对 |
| 运行截图 | evidence/screenshots/ 或 docs/images/ | 项目真实运行页面 | 与正文 UI 描述匹配 | 人工视检 |
| 测试日志 | evidence/test-data/ | 控制台或中间件日志 | 测试场景与结论吻合 | 数据戳真实性核对 |
| 性能数据 | evidence/test-data/ | 压测工具、监控面板 | 必须体现明确环境与工具 | 指标合理性与来源复核 |
| 架构图源文件 | evidence/diagrams/ | nature-figure 或白板设计 | 反映当前后端结构 | 拓扑正确性确认 |
| E-R 图源文件 | evidence/diagrams/ | nature-figure 配合 DDL | 包含核心表与主外键 | 数据库模式对齐 |
| 外部研究摘要 | literature/ | 独立检索工具 | 存在明确引用出处 | 查阅源出处避免幻觉 |
| 图表登记 | evidence/figure-table-register.md | 手工维护或流程脚本 | 图表物理存在 | 注册表比对 |

## 8. PaperSpine 职责边界

| Skill | 当前安装状态 | 项目中职责 | 触发条件 | 输入 | 输出 | 禁止事项 | 是否纳入当前流程 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| paper-spine | 已安装 | 流程中控编排 | 大修重写需调度 | 任务意图 | 子模块调度 | 全局直接复写 | 是 |
| paper-spine-intake | 已安装 | 配置注入 | 起步阶段 | 用户需求 | config | 无 | 是 |
| paper-spine-research | 已安装 | 背景文献挖掘 | 需求阶段 | 方向参数 | 调研材料 | 替代事实 | 是 |
| paper-spine-citation | 已安装 | 引用支撑建立 | 补充引源 | 源文献 | 格式化记录 | 直接改写正文编号 | 是 |
| paper-spine-rewrite | 已安装 | 单章脱水或大修 | 证据已锁定 | 证据与原章 | 新 Markdown | 批量覆盖多章 | 是 |
| paper-spine-build | 已安装 | 骨架生成 | (已废弃) | (已废弃) | 目录 | 替代现有唯一真相源 | 否 |
| paper-spine-latex | 已安装 | LaTeX 配置 | (已废弃) | (已废弃) | tex 产物 | 本项目不走 LaTeX | 否 |
| paper-spine-audit | 已安装 | 事后逻辑校验 | 单章改写后 | diff 与要求 | 审计报告 | 直接修改源文件 | 是 |
| paper-spine-ui | 已安装 | 交互界面配置 | 流程设定 | UI 交互 | 配置文件 | 绕过 governance 规范 | 是 |

注意：
1. paper-spine-rewrite 只能单章执行。
2. paper-spine-rewrite 启动前必须完成证据锁定。
3. paper-spine-audit 只能审计，不得直接修改源文件。
4. paper-spine-citation 输出只能进入 references.bib 草案候选，需 quality-review.md 复核后才能使用。
5. paper-spine-build 当前禁用，因为本论文已有正文。
6. paper-spine-latex 当前禁用，因为本项目不走 LaTeX/PDF 自动链路。
7. paper-spine-ui 仅作为配置入口，不得绕过 workflow-governance.md。
8. PaperSpine 不能替代用户和审查官的验收判断。
9. paper-spine-humanize、paper-spine-translate、paper-spine-update 上游存在但当前未锁定安装，不纳入当前流程。

## 9. nature-skills 职责边界

| Skill | 当前安装状态 | 项目中职责 | 触发条件 | 输入 | 输出 | 禁止事项 | 是否纳入当前流程 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| nature-figure | 已安装 | 视觉严谨的工程/科研图表生成 | 架构或数据层变动 | 真实代码、DDL或测试数据 | 图表文件 | 绘制营销风格图表 | 是 |

注意：
1. 当前只确认安装 nature-figure。
2. nature-figure 可用于严肃工程/科研图表视觉重绘。
3. 架构事实仍由代码、DDL、接口时序、Mermaid/draw.io、人审决定。
4. 架构图、E-R 图优先 Mermaid / draw.io / 手工审查。
5. nature-figure 仅作为可选美化、多面板科研图或导出工具。
6. nature-figure 不得绘制营销图。
7. nature-figure 不得脱离代码事实自行想象架构。
8. nature-polishing、nature-citation、nature-academic-search、nature-data 当前未锁定安装，不纳入当前流程。
9. 即使未来安装，nature-polishing 也不能作为中文本科工程论文默认润色器。
10. 即使未来安装，nature-citation / nature-academic-search 输出也必须先进入 literature/。

## 10. GPT / Gemini / NotebookLM 职责边界

| 工具 | 适合用途 | 不适合用途 | 输入材料 | 输出去向 | 进入正文前置条件 |
| --- | --- | --- | --- | --- | --- |
| GPT Deep Research | 外部文献综述、官方文档核验、竞品调研、AI/教育补充研究 | 挖掘或替代本地私有代码实现 | 研究 Prompt | 外部研究结论必须先完成来源核验，并同步到文献质量复核表与证据映射表；未经用户和审查官确认，不得进入正文。 | 人工查证、确认无幻觉 |
| Gemini Deep Research | 交叉验证、不同检索生态补充 | 替代事实逻辑 | 研究 Prompt | 同上 | 同上 |
| NotebookLM | 本地材料源内问答、矛盾检测、证据回溯 | 直接作为正文生成器或终版输出 | 本地代码与文档全集包 | 终端对话支撑决策 | 仅作参考不进入正文 |

注意：
1. GPT Deep Research 适合外部文献综述、官方文档核验、竞品/相似系统调研、LLM/教育/AI 面试相关研究补充。
2. Gemini Deep Research 适合交叉验证和不同检索生态补充。
3. NotebookLM 适合上传本地材料后做源内问答、矛盾检测、证据回溯。
4. 三者都不能直接覆盖 chapters/*.md。

6. 研究结论进入正文前必须具备来源、用途、适配章节和人工确认。
7. 深度研究不能替代代码事实。
8. NotebookLM 输出只能作为人类决策支撑，不能作为最终正文来源。

## 11. 引用体系治理

1. 当前仍使用裸 [1]、[1-2]、[3,5-7] 编号。
2. 正文未冻结前，不频繁调整参考文献编号。
3. BibTeX + CSL + citeproc 如需迁移，必须作为独立阶段。
4. paper-spine-citation、nature-citation、nature-academic-search、GPT/Gemini Deep Research 的输出不能直接进入正文编号。
5. 所有参考文献必须先进入 literature/ 审核。
6. 官方技术文档原则上用于第三/四章技术实现支撑，不应挤占第一/二章理论研究主文献位置。
7. 内容冻结后，才能集中核对正文编号、参考文献列表、evidence-map.md、references.bib。

## 12. DOCX / PDF / Word 人工终审边界

只有同时满足以下条件，才允许执行 build-docx.ps1：

1. 图表冻结；
2. 证据冻结；
3. 文献冻结；
4. 正文冻结；
5. 引用冻结；
6. 用户与审查官明确确认。

build-docx.ps1 只生成 thesis-working-draft.docx 工作稿。
提交版 DOCX/PDF 只能在 Word/WPS 中人工终审后产生。
PDF 必须由人工终审后的 DOCX 导出。
不得在资产未冻结前生成最终 Word 或 PDF。

| 阶段 | 产物 | 是否自动化 | 是否可作为提交版 | 验收人 |
| --- | --- | --- | --- | --- |
| DOCX 工作稿构建 | thesis-working-draft.docx | 是 (build-docx.ps1) | 否 | 开发者自测 |
| Word 人工终审 | submitted-thesis.docx | 否 (手工) | 是 | 导师 / 作者 |
| PDF 导出 | submitted-thesis.pdf | 否 (手工) | 是 | 盲审 / 答辩组 |
| 最终冻结归档 | Git tag / 打包版本 | 否 (手工 Git 提交) | 最终存档版 | 所有人 |

## 13. 文件命名与归档规则

1. thesis-working-draft.docx：自动构建工作稿。
2. submitted-thesis.docx：本地人工终审后的提交版 DOCX，不由 Agent 自动生成。
3. submitted-thesis.pdf：由人工终审 DOCX 导出的提交版 PDF。
4. thesis-assembled.md：自动拼接中间文件，不得手工维护。
5. 最终提交后，应记录 commit SHA 或 Git tag。
6. 任何提交版文件如需入库，必须由用户明确授权。

## 14. 完整论文生命周期

| 阶段 | 触发条件 | 输入 | 允许工具 | 禁止工具 | 输出 | 验收标准 | 是否需要用户与审查官复核 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A. 项目变更感知 | 主干分支代码、配置、框架版本等发生变动 | Git log、源码 diff | NotebookLM | 修改正文的任何 Agent | 变更评估清单 | 明确论文是否需随动 | 是 |
| B. 证据采集与登记 | 评估结论为需修改 | 系统运行日志、代码、截图 | 截屏工具、终端日志抓取 | paper-spine | 补充的 evidence/ | 数据真实可靠不造假 | 是 |
| C. 图表/测试数据同步判断 | 数据库表更新、架构颠覆 | 最新代码与设计模式 | nature-figure、Mermaid | - | 对应图件文件 | 拓扑或关系一致无误 | 是 |
| D. 外部研究补充判断 | 相关文献空缺或需要外部理论背景支撑 | literature/ | GPT/Gemini Deep Research | 直接修改 chapters/ 的 Agent | 调研报告存放缓冲区 | 无幻觉文献来源追溯通过 | 是 |
| E. 正文单章修订或降噪 | 证据确认锁定 | evidence/、单章节 Markdown | paper-spine-rewrite/audit | paper-spine-build、一次性全局改写 | 更新后的单章节 MD | 无异常营销修辞与逻辑事实错乱 | 是 |
| F. 引用与参考文献整理 | 章节脱水导致引用号错位 | evidence-map.md | 手工文本编辑 | citeproc 自动排版（当前阶段暂缓） | 连续引用的正确编号 | 编号对应事实匹配 | 是 |
| G. DOCX 工作稿构建 | 全部章节及引用内容确认冻结 | chapters/ 下文件集 | build-docx.ps1 | LaTeX | thesis-working-draft.docx | 脚本无退出错误返回 | 是 |
| H. Word 人工终审 | 工作稿已构建生成 | DOCX 工作稿 | Word/WPS | 任何自动化工具操作最终排版 | submitted-thesis.docx | 手工核对排版正确格式无残缺 | 是 |
| I. PDF 导出与最终验收 | Word 排版结束无误 | 终审的 DOCX | Word/WPS 导出 PDF 功能 | Pandoc | submitted-thesis.pdf | PDF 完美无乱码 | 是 |
| J. 冻结归档 | PDF 及 DOCX 均验收合格 | submitted-thesis.* 产物 | Git | - | Tag 或 Commit 号归档 | 文档归档提交不可更改 | 是 |

注意：每个阶段完成后，是否需要用户与审查官复核：是。

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
- 引用影响判断

| 漂移类型 | 是否触发审查 | 影响资产 | 禁止直接操作 |
| --- | --- | --- | --- |
| 架构与链路 | 是 | evidence/、架构图 | 直接修改正文 |
| 数据库表 | 是 | evidence/、E-R图 | 直接修改正文 |
| 业务逻辑 | 是 | evidence/、用例图 | 直接修改正文 |
| 测试与演示 | 是 | evidence/、测试记录 | 直接修改正文 |

## 双 Deep Research 报告规则

本论文后续必须形成两份本地研究报告资产：

1. GPT Deep Research 报告（已归档弃用）。
2. Gemini Deep Research 报告（已归档弃用）。

两份报告均要求：
- 中文为主；
- 保留英文文献原题；
- 保留 DOI、arXiv ID、出版社页、期刊页或官方链接；
- 不直接进入 chapters/*.md；
- 不直接改正文引用编号；
- 不直接覆盖 quality-review.md；
- 必须经过人工核验后，才允许进入正式参考文献候选池。

GPT Deep Research：
LLM 驱动的模拟面试、简历诊断、教育训练系统研究综述。

Gemini Deep Research：
AI interview training、resume-job matching、LLM education assistant 的交叉验证报告。

## 参考文献翻新规则

参考文献必须执行一轮独立翻新，不允许直接沿用旧文献表进入最终论文。

翻新目标：
- 保证高可信候选进入 references.bib；
- 官方技术文档只进入第二章、第三章、第四章等技术依据位置，不得挤占第一章/第二章研究现状核心文献位置。

质量规则：
- A / A- / B+ 优先进入正式参考文献；
- B- 仅在高度贴题且无更好替代时保留；
- C 类不得进入正式参考文献；
- 元数据不完整、无法核验 DOI/期刊页/出版社页/作者年份的文献不得进入正式参考文献；
- 大模型生成的文献条目必须人工核验，未核验不得引用。

阶段 2.9：文献检索、质量筛选与参考文献翻新

阶段 2.9 不允许直接修改正文。新文献只允许进入：
- thesis-assets/literature/quality-review.md
- thesis-assets/literature/evidence-map.md
- thesis-assets/literature/references.bib

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

## gzu-thesis-template.docx 定位规则

gzu-thesis-template.docx 只作为学校格式、样式、页边距、标题层级、目录、页眉页脚等格式参考。

即使 gzu-thesis-template.docx 当前包含全量论文叙述，也不得作为正文事实来源。

论文正文事实来源只能是：
1. chapters/*.md；
2. evidence/；
3. literature/references.bib；
4. literature/quality-review.md；
5. literature/evidence-map.md。

任何论文文字叙述改写，只允许修改 chapters/*.md。
不得从 gzu-thesis-template.docx 反向同步正文。
不得以 thesis-working-draft.docx 或 gzu-thesis-template.docx 作为正文修改源。









