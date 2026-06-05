# Agent 工作协议与操作规范

## 1. 默认基准与交互原则
- **环境基准**：Windows 11、PowerShell 7+、UTF-8。
- **沟通基准**：结论先行。回复简洁、直接、可执行。
- **诚实底线**：不要编造；不确定就明确说不确定。
- **免解释原则**：不要输出元解释，不要解释你正在遵守哪些规则，除非被明确要求。
- **交互效率**：
  - 需求明确就直接执行，不反复确认。
  - 只问真正影响结果的问题。
  - 不重复问已提供的信息。
  - 不要逐条解释规则或过程，除非被明确要求。

## 2. 决策与思考规范 (先想清楚再动手)
- 不要擅自假设，不要隐藏歧义。
- 仅当不同解读会实质影响实现、输出或风险时，才列出分歧。
- 关键信息不足且会影响结果时，先指出缺失；否则做最稳妥假设并明确说明。
- 基于假设继续时，保持方案最小、可回退，并在结尾指出需要验证的关键假设。

## 3. 目标驱动与验证体系
- **标准先行**：先明确完成标准，再实现；若请求已清晰且完成标准不影响方案选择，直接执行，不必展开说明。不要用“应该可以”作为完成标准。
- **修 Bug 工作流**：答复顺序严格为：1. 结论与修复摘要 -> 2. 复现条件 -> 3. 修改与验证。
- **加功能工作流**：先明确完成标准，再实现。
- **验证优先级**：
  1. 先运行/复现
  2. 其次自动化测试
  3. 再次静态检查或最小手动验证
  - *降级机制*：无法执行上一层验证时，按顺序降级，并明确说明原因。无法验证时，必须明确说明“未验证”及原因。

## 4. 架构与代码修改原则
### 始终简洁优先
- 用最少的代码解决问题。
- 不做没被要求的功能。
- 不为一次性需求过度抽象。
- 优先稳定、清晰、易维护，不炫技。

### 始终精准修改
- 只改必须改的部分。
- 不顺手重构，不做无关优化，不扩大改动范围。
- 保持现有风格、命名和项目习惯。
- 仅当不提醒会导致修改失效、引入风险或误导时，才提及无关问题。
- 每一处改动都必须直接对应请求。

## 5. 产出与输出规范
- **方案选择**：默认提供最稳妥、兼容性好的方案。除非被明确要求，不引入新依赖、不增加新框架、不大改结构。
- **代码输出**：默认输出最小必要的 diff、补丁或片段。仅在以下情况才输出完整文件：明确要求、文件不超过 50 行、或局部输出不足以安全应用。
- **脚本与编码**：命令行默认使用 PowerShell。文本读写默认显式使用 UTF-8。
- **环境依赖预警**：若方案依赖 PowerShell 5.1、Windows Server、特定终端或特定编码行为，必须明确说明。

## 6. 多步骤任务处理
- 默认一次性完成所有直接相关且信息充足的步骤，不为普通中间步骤反复确认。
- **边界停顿**：仅当后续步骤依赖人为选择、外部执行结果、或属于高风险操作时，才停在边界点并明确说明下一步需要什么。

## 7. 工具集成：CodeGraph MCP
项目已接入 CodeGraph MCP，Agent 应利用以下工具功能提升上下文理解与修改精准度：

| 工具名 | 功能说明 |
| :--- | :--- |
| `codegraph_search` | 按名称搜索符号 |
| `codegraph_context` | 构建任务相关的代码上下文 |
| `codegraph_trace` | 追踪两个符号之间的调用路径 |
| `codegraph_callers` | 查找谁调用了某个函数 |
| `codegraph_callees` | 查找某个函数调用了谁 |
| `codegraph_impact` | 分析修改某个符号会影响哪些代码 |
| `codegraph_node` | 获取某个符号的详细信息（含源码） |
| `codegraph_explore` | 批量返回多个相关符号的源码 |
| `codegraph_files` | 获取索引的文件结构 |
| `codegraph_status` | 检查索引健康状态和统计 |

- **维护更新**：
  - 日常代码修改后，执行增量同步（仅处理变更文件，秒级完成）：
    ```powershell
    codegraph sync
    ```
  - 首次初始化或索引损坏时，执行全量重建：
    ```powershell
    codegraph index
    ```

## 8. 论文自动化编排与渲染管线 (PaperSpine + Pandoc)
本项目已全量剥离“手工写论文”的原始模式，Agent 在处理论文需求时必须严格恪守以下自动化工作流：

### 8.1 绝对的“单一真相源”纪律
- **禁止在 Word 中修改**：任何对论文正文的增、删、改、逻辑重组，**只能**且必须在 `thesis-assets/chapters/*.md` 对应的分章文件中进行。
- 最终产物 `thesis-assets/current/thesis-final.docx` 为单向渲染输出，绝不允许对其进行任何自动化代码的回写与破坏。

### 8.2 大修与逻辑降维 (PaperSpine 引擎)
- 当面临代码更新引发的论文逻辑大修时，切勿使用原生对话模型直接生成长篇大论。
- 必须遵循工业级管线：将真实证据（日志、代码片段、新图表等）存入 `thesis-assets/evidence/` -> 修改 `thesis-assets/paperspine-execution-plan.md` -> 启动 PaperSpine 引擎执行 `rewrite_existing`。
- **单章执行纪律**：每次只对一个 `chapters/*.md` 文件执行 `rewrite_existing`，严禁将全书合并后一次性送入 PaperSpine，否则会触发上下文截断崩溃。

### 8.3 物理渲染流 (Pandoc 一键合版)
- 在 `chapters/*.md` 确认无误后，必须通过 PowerShell 执行以下指令生成终稿：
  ```powershell
  # 在 thesis-assets 目录下执行组合与渲染
  $chapters = @("chapters\abstract-keywords.md", "chapters\chapter-01-introduction.md", "chapters\chapter-02-related-tech.md", "chapters\chapter-03-analysis-design.md", "chapters\chapter-04-implementation.md", "chapters\chapter-05-testing.md", "chapters\chapter-06-conclusion.md")
  Clear-Content thesis-full.md -ErrorAction SilentlyContinue
  foreach ($file in $chapters) { Get-Content $file -Encoding utf8 -Raw | Add-Content thesis-full.md -Encoding utf8 -NoNewline; Add-Content thesis-full.md "`n`n" -Encoding utf8 }
  
  pandoc thesis-full.md -o current\thesis-final.docx `
    --reference-doc=meta\school-template.docx `
    --toc --toc-depth=3 `
    -f markdown -t docx
  ```

### 8.4 边界防线 (Last 5 Miles 与独立答辩)
- **人工收尾隔离**：在 Agent 成功渲染完 `thesis-final.docx` 后，必须明确提醒用户接管“最后的 5%”（手工贴图、粘贴附录/参考文献、更新目录域与底端页码），严禁 Agent 尝试自动化实现这些行为。
- **答辩材料独立**：答辩 PPT 逻辑映射、演讲稿及 Q&A 演练完全独立于论文正文生成管线，此类任务请直接查阅并操作 `thesis-assets/defense/` 目录。

> 详细流程参见：`thesis-assets/paperspine-execution-plan.md`