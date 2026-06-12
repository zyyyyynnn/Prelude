# thesis-assets 论文材料总索引

> **🔵 核心红线声明**：`chapters/*.md` 是全卷正文的**唯一真相源（Source of Truth）**。
> 任何对论文正文的增删改必须在此处进行。我们已经全量部署 PaperSpine 自动化重写引擎与 Pandoc 渲染引擎，传统的局部手工维护模式已彻底废弃。

## 当前终极目录拓扑

```text
thesis-assets/
├── README.md                    ← 本文件：更新后的全局索引
├── paperspine-workflow.md       ← 管线调度规范：定义所有的 PaperSpine 行为与重写沙盒边界
├── thesis-full.md               ← 全书物理合并版：由单章组合而成，是 Pandoc 渲染的直接输入源
├── chapters/                    ← 🔵 唯一真相源：包含从摘要到第六章的 7 个最终定稿文件
├── evidence/                    ← 证据材料：系统截图、日志、代码段（供 PaperSpine 读取）
├── literature/                  ← 文献管理：文献包与引用锚点
├── defense/                     ← 答辩材料：独立的答辩演练、PPT 映射与演讲稿归档
├── meta/                        ← 管理文件与排版模板：包含极其重要的 school-template.docx
│   └── workflow-governance.md   ← 论文生命周期与工具职责治理规范
├── current/
│   └── thesis-final.docx        ← DOCX 自动构建工作稿：由 thesis-full.md 与母版结合生成，不能直接提交
└── archive/                     ← 历史归档（仅供只读回溯，绝不参与当前生成流）
    ├── legacy/                  ← 淘汰的老旧体系
    │   ├── drafts-original/
    │   ├── thesis-polished.md   ← 历史旧版的完整 Markdown
    │   ├── thesis-control.md    ← 已被 paperspine-workflow.md 取代的旧总控
    │   └── thesis-handbook/     ← 曾经的手工作坊式 Prompt 教程（14 个文件已全部陪葬）
    └── matrices/                ← 历次重写矩阵（Rationale Matrices）
```

修改任何论文资产前，必须先阅读 meta/workflow-governance.md。
workflow-governance.md 是论文资产治理最高规范。

## 交付与渲染管线指南

**1. 修改内容**
请永远在 `chapters/` 目录中定位到对应的 `.md` 文件进行文本修订。

**2. 引入证据大修**
若进行了代码重大升级，将代码片段放入 `evidence/`，随后在确认证据无误后，启动 Agent 自动执行 `rewrite_existing`（当前论文流程入口为 `paperspine-workflow.md`。若未来需要动态调度文件，可另建 `paperspine-execution-plan.md`；当前仓库不依赖该文件）。

**3. 生成 DOCX 工作稿**
```powershell
pwsh -ExecutionPolicy Bypass -File .\thesis-assets\build-docx.ps1
```
该命令只生成 current/thesis-final.docx 工作稿。提交版 DOCX/PDF 必须在内容冻结、引用冻结后由人工在 Word/WPS 中终审产生。
*(注：手工 Pandoc 命令仅用于排查，不作为推荐入口。正式入口以 build-docx.ps1 为准。)*

**4. 终极人工检查（Last 5 Miles）**
- 打开 `current\thesis-final.docx`。
- 将旧版遗留的图片按需手工粘贴至对应文本处。
- 粘贴参考文献与附录。
- 右键更新全部目录，并插入底端页码。

**5. PDF 路线说明**
当前阶段只自动生成 DOCX。PDF 建议在 Word/WPS 中完成目录域更新、页眉页脚、页码、图题表题人工终审后导出。暂不引入 Pandoc-LaTeX PDF 自动链路，避免中文模板、页眉页脚和学校格式失真。
