# 毕设全流程手册导航

本目录默认采用 `Codex 主导` 的协作方式：人工侧负责提供原始素材、真实数据、学校要求与外部研究报告，协作模型负责整理、写作、整合、校核与最终产出。除必须由人工侧亲自完成的取材、截图、知网检索和真实结果采集外，其余多数任务都可以提供给协作模型完成。

## 推荐使用顺序

`00 → 00.5 → 01/02 → 03 → 03.5 → 03.8 → 04/05/06 → 07/07.5 → 08`

## 文档导航

### 基础入口

- [00-全局背景与使用说明.md](./00-全局背景与使用说明.md)：新开对话先发送的全局背景块，以及 Codex 默认协作方式。
- [00.5-文献检索与协作工作流.md](./00.5-文献检索与协作工作流.md)：知网、Semantic Scholar MCP、GPT/Gemini deep research 报告与 Codex 的文献协作流程。

### 论文正文

- [01-第一章-绪论.md](./01-第一章-绪论.md)：把文献要点、竞品材料和字数要求提供给协作模型，协作模型会写绪论。
- [02-第二章-相关技术综述.md](./02-第二章-相关技术综述.md)：把技术版本、选型依据和老师关注点提供给协作模型，协作模型会组织成正文与答辩口径。
- [03-第三章-系统分析与设计.md](./03-第三章-系统分析与设计.md)：图表和设计信息准备好后提供给协作模型，协作模型会补写分析与设计章节。
- [04-第四章-系统实现.md](./04-第四章-系统实现.md)：把真实代码片段和开发记录提供给协作模型，协作模型会扩写实现部分。
- [05-第五章-系统测试.md](./05-第五章-系统测试.md)：把真实测试数据和环境信息提供给协作模型，协作模型会生成测试章节。
- [06-第六章-总结与摘要.md](./06-第六章-总结与摘要.md)：历史文件名保留；当前正文口径为“第六章总结与展望”，中英文摘要已独立前置。
- [07-全卷打磨与格式核查.md](./07-全卷打磨与格式核查.md)：把章节原文、格式要求和核查目标提供给协作模型，协作模型会完成润色、降重、逻辑检查与终审。
- [07.5-参考文献整理.md](./07.5-参考文献整理.md)：把原始条目信息和访问日期提供给协作模型，协作模型会整理文末参考文献。

### 开发与素材

- [03.5-系统开发规格书.md](./03.5-系统开发规格书.md)：优先交给 Codex 直接实现的主规格书。
- [03.8-写论文前素材归档.md](./03.8-写论文前素材归档.md)：真实截图、代码片段、测试数据与开发记录归档指南。

### 答辩准备

- [08-答辩准备与动态演练.md](./08-答辩准备与动态演练.md)：把真实系统状态、截图、测试结果和个人开发经历提供给协作模型，协作模型会准备答辩材料与演练。

## 素材模板

- [assets-template/README.md](./assets-template/README.md)
- [assets-template/dev-notes-template.md](./assets-template/dev-notes-template.md)
- [assets-template/literature-evidence-map-template.md](./assets-template/literature-evidence-map-template.md)
- [assets-template/final-evidence-lock-template.md](./assets-template/final-evidence-lock-template.md)
- [assets-template/figure-table-register-template.md](./assets-template/figure-table-register-template.md)
- [assets-template/defense-slide-map-template.md](./assets-template/defense-slide-map-template.md)

## 执行总控与当前材料

- [../thesis-assets/README.md](../thesis-assets/README.md)：论文材料目录说明，包含主稿、当前交付物、文献、图表、测试和答辩材料放置规则。
- [../thesis-assets/thesis-control.md](../thesis-assets/thesis-control.md)：当前状态唯一总控。
- [../thesis-assets/material-checklist.md](../thesis-assets/material-checklist.md)：资料包维护清单。
- [../thesis-assets/final-evidence-lock.md](../thesis-assets/final-evidence-lock.md)：最终证据锁版记录。
- [../thesis-assets/final-supplement-checklist.md](../thesis-assets/final-supplement-checklist.md)：终稿前已完成、仍需人工终审、预留待测和可选补充清单。
- [../thesis-assets/archive/legacy-reports/school-requirements-package.md](../thesis-assets/archive/legacy-reports/school-requirements-package.md)：学校要求包阶段记录。
- [../thesis-assets/archive/legacy-reports/贵州大学毕业论文（设计）管理办法.pdf](../thesis-assets/archive/legacy-reports/贵州大学毕业论文（设计）管理办法.pdf)：学校论文设计管理办法 PDF。

## 使用建议

- 每次新开对话，先参考 [00-全局背景与使用说明.md](./00-全局背景与使用说明.md) 发送背景块。
- 涉及文献时，默认按“方案 A”执行：`BibTeX + 摘要 + 原文要点 + deep research 报告（如有）`，再按 [00.5-文献检索与协作工作流.md](./00.5-文献检索与协作工作流.md) 提供给协作模型。
- 默认不要把同一任务拆成多个模型分别执行；若人工侧已经拿到 GPT 或 Gemini 的 deep research 报告，直接把报告原文提供给协作模型，协作模型会吸收、比对和落地。
- 在各阶段推进中，Codex 可适当、合理地调用本地 skills 与 MCP 工具提升检索、抽取、核验与整理效率，但所有输出仍以真实材料和已确认事实为准。
- 相关提示词均已包含在对应文档中，无需额外查找模板文件。
- 若论文已接近完成，继续用 [07-全卷打磨与格式核查.md](./07-全卷打磨与格式核查.md) 做一次“最终提交包核查”。
- 日常优先维护 [../thesis-assets/thesis-control.md](../thesis-assets/thesis-control.md)、[../thesis-assets/final-evidence-lock.md](../thesis-assets/final-evidence-lock.md) 和 [../thesis-assets/README.md](../thesis-assets/README.md)。
- 当前图表编号统一采用 `图3.1`、`表5.1` 点号格式；若阶段记录中出现旧编号，以当前总控和图表登记表为准。
- 当前 Word 主稿已并入封面和诚信责任书；终稿前仍需人工填写或确认签名、日期、指导教师，并更新目录域和页码。
- `贵州大学答辩PPT模板.pptx` 当前未入库；如需使用，需人工重新提供或从历史提交恢复到 `thesis-assets/defense/贵州大学答辩PPT模板.pptx`。
- 默认采用“阶段断点审查”机制：每个阶段完成后，先提交该阶段交付物并人工审查，确认后再进入下一阶段。
