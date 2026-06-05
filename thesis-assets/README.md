# thesis-assets 论文材料总索引

> **🔵 核心红线声明**：`chapters/*.md` 是全卷正文的**唯一真相源（Source of Truth）**。
> 任何对论文正文的增删改必须在此处进行。我们已经全量部署 PaperSpine 自动化重写引擎与 Pandoc 渲染引擎，传统的局部手工维护模式已彻底废弃。

## 当前终极目录拓扑

```text
thesis-assets/
├── README.md                    ← 本文件：更新后的全局索引
├── paperspine-execution-plan.md ← 管线调度中心：定义所有的 PaperSpine 行为与重写沙盒边界
├── thesis-full.md               ← 全书物理合并版：由单章组合而成，是 Pandoc 渲染的直接输入源
├── chapters/                    ← 🔵 唯一真相源：包含从摘要到第六章的 7 个最终定稿文件
├── evidence/                    ← 证据材料：系统截图、日志、代码段（供 PaperSpine 读取）
├── literature/                  ← 文献管理：文献包与引用锚点
├── defense/                     ← 答辩材料：独立的答辩演练、PPT 映射与演讲稿归档
├── meta/                        ← 管理文件与排版模板：包含极其重要的 school-template.docx
├── current/
│   └── thesis-final.docx        ← Pandoc 终稿产物：由 thesis-full.md 与母版结合生成的最终交付物
└── archive/                     ← 历史归档（仅供只读回溯，绝不参与当前生成流）
    ├── legacy/                  ← 淘汰的老旧体系
    │   ├── drafts-original/
    │   ├── thesis-polished.md   ← 历史旧版的完整 Markdown
    │   ├── thesis-control.md    ← 已被 paperspine-execution-plan 取代的旧总控
    │   └── thesis-handbook/     ← 曾经的手工作坊式 Prompt 教程（14 个文件已全部陪葬）
    ├── matrices/                ← 历次重写矩阵（Rationale Matrices）
    ├── sync-merged/             
    └── process-reports/         ← 历史过程报告
```

## 交付与渲染管线指南

**1. 修改内容**
请永远在 `chapters/` 目录中定位到对应的 `.md` 文件进行文本修订。

**2. 引入证据大修**
若进行了代码重大升级，将代码片段放入 `evidence/`，并在 `paperspine-execution-plan.md` 中指向该证据，启动 Agent 自动执行 `rewrite_existing`。

**3. 生成最终 Word**
```powershell
pandoc thesis-full.md -o current\thesis-final.docx --reference-doc=meta\school-template.docx
```

**4. 终极人工检查（Last 5 Miles）**
- 打开 `current\thesis-final.docx`。
- 将旧版遗留的图片按需手工粘贴至对应文本处。
- 粘贴参考文献与附录。
- 右键更新全部目录，并插入底端页码。
