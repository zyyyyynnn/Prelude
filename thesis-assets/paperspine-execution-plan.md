# PaperSpine × thesis-assets 整合执行方案

> 最后更新：2026-06-05
> 状态：待执行

---

## 背景

thesis-assets 目录存在三套平行论文版本（drafts/、process/sync/、current/），真相源不明，Word 文档散落根目录，管理文件与交付物混杂。现需结合 PaperSpine（论文写作技能套件）执行"方案 B：单一真相源"重构，将目录架构升级为工业级论文生产流水线。

---

## 核心原则

1. **单章执行，不合并**：每一章作为独立的 `rewrite_existing` 任务，避免 64KB 全文导致 writing_rationale_matrix 过载和上下文截断。
2. **scene=report_review**：不用 `journal`，毕业论文需要保留长篇幅与系统实现细节。
3. **灰度先行**：先用 chapter-01（绪论）跑通全流程，确认文风和逻辑深度后再批量执行。
4. **证据前置**：处理"系统实现"章前，确保 evidence/ 下有充足的代码片段和截图说明，否则 PaperSpine 会为了"逻辑严密"而删减缺乏强支撑的系统描述。
5. **纯物理终稿**：单章 PaperSpine 输出即为内容终稿，终稿阶段仅做物理拼接和格式转换，**严禁将合并后的全文再次送入 PaperSpine rewrite**。

---

## 第一阶段：目录重组（方案 B 落地）

### 步骤 1.1：创建新目录结构

```
thesis-assets/
├── chapters/                  ← 新建目录，存放拆分后的最新单章，作为唯一真相源
├── current/                   ← 最终产物发布区（编译输出）
├── meta/                      ← 管理文件集中（从根目录迁入）
├── paper_rewriting_output/    ← PaperSpine 强制工作空间（自动创建）
├── evidence/                  ← 不变
├── literature/                ← 不变
├── defense/                   ← 不变
├── process/                   ← 保留 reports/，sync/ 移入 archive/
└── archive/                   ← 归档旧版本
    ├── sync-merged/           ← 原 process/sync/ 移入
    ├── matrices/              ← 每章的逻辑矩阵归档
    └── legacy/
```

### 步骤 1.2：文件迁移清单

| 源 | 目标 | 操作 |
|---|------|------|
| `drafts/` 目录及全部内容 | `archive/legacy/drafts-original/` | **整体归档**（彻底废弃旧版本） |
| `current/thesis-polished.md` | `current/thesis-polished.md` | **不动，作为本次重构的唯一输入基准（待拆分）** |
| 根目录 `毕业论文正式版（草稿）.docx` | `meta/` | 移动 |
| `material-checklist.md` | `meta/` | 移动 |
| `final-evidence-lock.md` | `meta/` | 移动 |
| `final-supplement-checklist.md` | `meta/` | 移动 |
| `current/毕业论文正式版（润色回填）.docx` | `meta/school-template.docx` | **移动并重命名（作为排版样式基准模板）** |
| `process/sync/*.sync.md` | `archive/sync-merged/` | 归档 |

### 步骤 1.3：更新 README.md

在 README 顶部增加"单一真相源"声明：

```
> **真相源声明**：`chapters/` 下的分章节 Markdown 是论文的唯一编辑源。
> PaperSpine 单章输出提取回 `chapters/` 后即为该章内容终稿。
> `paper_rewriting_output/` 是 PaperSpine 运行时工作空间，不保留跨章节状态。
> `archive/` 存放历史版本，仅供回溯。
```

### 步骤 1.4：拆分最新基准文件（关键前置操作）

鉴于 `current/thesis-polished.md` 是包含全书的最新基准版，而 PaperSpine 必须分章执行以防过载。**在启动自动化流程前，必须进行纯物理拆分：**

1. 在新建的 `chapters/` 目录下，手动创建 7 个空白 markdown 文件（对应绪论至摘要）。
2. 打开 `current/thesis-polished.md`，按章节标题将其内容手工**剪切并粘贴**到对应的 7 个单章文件中。
3. 拆分完成后，这 7 个文件即成为后续步骤中 `draft_path` 的真正输入源。
4. 拆分完毕后，将原 `current/thesis-polished.md` 移入 `archive/legacy/` 备份。

---

## 第二阶段：阻塞项前置修复

在启动 PaperSpine 前，先修复证据腐化问题：

| # | 文件 | 问题 | 修复 |
|---|------|------|------|
| 1 | `impl-2026-05-31.md` 证据 4 | 引用了已删除的正则 Pattern | 标记为"已被证据 9 替代"，添加弃用声明 |
| 2 | `impl-2026-05-31.md` 证据 7 | 文件路径已重命名 | 更新为 `LlmSettingsPanel.vue` + `useLlmSettings.ts` |
| 3 | `impl-2026-05-31.md` 证据 5/8 | 行号偏移 | 核对实际行号并更新 |
| 4 | `impl-2026-06-02.md` 证据 10 | 行号偏移 | 更新为 L57 |
| 5 | `impl-2026-06-05.md` 证据 13 | 行号偏移 | 更新为 L507 |

---

## 第三阶段：分章节批量执行（含目录覆写防护）

### 执行顺序

| 顺序 | 章节 | draft_path | 特殊注意事项 |
|------|------|-----------|-------------|
| 1 | 第一章 绪论 | `chapters/chapter-01-introduction.md` | 灰度测试章 |
| 2 | 第二章 相关技术 | `chapters/chapter-02-related-tech.md` | 技术描述不删减 |
| 3 | 第三章 分析设计 | `chapters/chapter-03-analysis-design.md` | 保留图表引用 |
| 4 | 第四章 系统实现 | `chapters/chapter-04-implementation.md` | ⚠️ 高危：确保 evidence/ 充足 |
| 5 | 第五章 系统测试 | `chapters/chapter-05-testing.md` | 保留测试用例表格 |
| 6 | 第六章 总结展望 | `chapters/chapter-06-conclusion.md` | 最短章节 |
| 7 | 摘要 | `chapters/abstract-keywords.md` | 最后处理 |

### 单章执行流程

对每一章重复以下步骤：

#### A. 配置 PaperSpine

```json
{
  "workflow": "rewrite_existing",
  "scene": "report_review",
  "tier": "pro",
  "output_language": "zh",
  "target_name": "基于大语言模型的沉浸式模拟面试与简历诊断系统",
  "materials_dir": "evidence",
  "draft_path": "chapters/chapter-XX-xxx.md",
  "user_motivation": "",
  "official_urls": [],
  "special_requirements": [
    "保持毕业论文的章节编号格式（X.1, X.2, ...）",
    "保留所有现有引用标记 [N]，不删除",
    "不压缩篇幅，保留详细论述",
    "保留所有系统实现细节和代码引用",
    "输出中文"
  ],
  "word_output": "none",
  "translation_package": "none",
  "reference_mode": "local_first",
  "reference_paths": ["literature"],
  "citation_target_count": 21
}
```

#### B. 执行 PaperSpine 流程

1. research → 生成研究档案
2. citation → 生成引用支持库
3. 确认动机 → 写入 `confirmed_motivation.md`
4. rewrite → 生成 `writing_rationale_matrix.md` + 重构章节
5. audit → 运行 `python scripts/integrity_audit.py paper_rewriting_output --markdown --write`

#### C. 提取产物并防覆写（关键步骤）

PaperSpine 的 `paper_rewriting_output/` 是硬编码目录，每章执行会覆盖上一章产物。**必须在每章完成后立即提取并备份：**

```powershell
# 1. 提取重写后的章节内容到 chapters/（覆盖原稿，成为新版真相源）
Copy-Item paper_rewriting_output\final_paper\latex_report.md chapters\chapter-XX-xxx.md -Force

# 2. 备份逻辑矩阵与完整性审计报告
New-Item -ItemType Directory -Force -Path archive\matrices | Out-Null
Copy-Item paper_rewriting_output\writing_rationale_matrix.md archive\matrices\matrix-chapXX.md -Force
Copy-Item paper_rewriting_output\integrity_audit.md archive\matrices\audit-chapXX.md -Force
```

#### D. 验收标准

| 检查项 | 通过条件 |
|--------|----------|
| 文风 | 不出现"期刊腔"过度压缩，保留毕业论文详述风格 |
| 引用 | 所有 [N] 标记保留，不丢失已有引用 |
| 篇幅 | 不少于原稿的 80% |
| 逻辑矩阵 | `writing_rationale_matrix.md` 可读、不超长 |
| 完整性审计 | 无 BLOCKED findings |
| 产物提取 | 章节已提取回 `chapters/`，矩阵已归档到 `archive/matrices/` |
| 防护校验 | **对于第四章等硬核章节，提取后必须运行 `git diff`，核查系统实现细节未被 AI 误删** |

**如果验收通过** → 清空 `paper_rewriting_output/`，进入下一章。
**如果验收失败** → 调整 `special_requirements` 或降级为 `tier: flash` 后重试。

### 灰度测试（第一章）

第一章作为灰度测试，额外关注：

- 观察 PaperSpine 重构后的文风和逻辑深度是否符合导师要求
- 确认 `writing_rationale_matrix.md` 的粒度和可读性
- 确认 `scene=report_review` 不会触发篇幅压缩

**灰度通过标准**：导师认可文风 + 篇幅无缩减 + 引用完整。
**灰度失败处理**：调整参数后重试，或放弃 PaperSpine 改为手工润色。

---

## 第四阶段：终稿构建（纯物理组装，无 AI 介入）

> **核心原则：单章 PaperSpine 输出即为内容终稿。严禁将合并全文再次送入 rewrite。**

### 步骤 4.1：物理合并章节

将 `chapters/` 下 7 个经过 PaperSpine 强化的单章 `.md`，按顺序用纯文本工具物理拼接：

```powershell
# 在 thesis-assets/ 目录下执行
$chapters = @(
    "chapters\abstract-keywords.md",
    "chapters\chapter-01-introduction.md",
    "chapters\chapter-02-related-tech.md",
    "chapters\chapter-03-analysis-design.md",
    "chapters\chapter-04-implementation.md",
    "chapters\chapter-05-testing.md",
    "chapters\chapter-06-conclusion.md"
)

Clear-Content thesis-full.md -ErrorAction SilentlyContinue
foreach ($file in $chapters) {
    Get-Content $file -Encoding utf8 -Raw | Add-Content thesis-full.md -Encoding utf8 -NoNewline
    Add-Content thesis-full.md "`n`n" -Encoding utf8
}
```

### 步骤 4.2：离线格式转换

**使用 Pandoc 将 `thesis-full.md` 导出为 Word**，不经过 PaperSpine：

```powershell
pandoc thesis-full.md -o current\thesis-final.docx `
  --reference-doc=meta\school-template.docx `
  --toc --toc-depth=3 `
  -f markdown -t docx
```

如果没有学校 Word 模板，省略 `--reference-doc` 参数。Pandoc 输出的 `.docx` 需要手工微调封面、页眉页脚、目录域。

### 步骤 4.3：学校模板适配（手工）

PaperSpine + Pandoc 输出的是"内容定稿"。以下项目需在 Word 中手工完成：

- 封面页导师姓名、签名、日期
- 诚信责任书签名
- 目录域更新（Ctrl+A → F9）
- 页码、页眉页脚格式
- 图题表题格式
- 参考文献格式（按学校 GB/T 7714 要求）

### 步骤 4.4：证据同步更新

更新 `meta/final-evidence-lock.md`，将 PaperSpine 产物路径同步到证据矩阵。

---

## 风险预案

| 风险 | 触发条件 | 应对 |
|------|----------|------|
| PaperSpine 删减实现细节 | 第四章 rewrite 后篇幅大幅缩减 | `special_requirements` 中明确"保留所有系统实现细节" |
| 引用编号被打乱 | rewrite 过程中重新排序引用 | `special_requirements` 中明确"保留原始 [N] 编号体系" |
| integrity_audit 报 BLOCKED | 缺少证据支撑的声明 | 补充 evidence/ 下的代码片段后重新 audit |
| 目录覆写丢失产物 | 忘记提取就启动下一章 | 每章完成后必须执行提取+归档脚本 |
| LaTeX 编译失败 | 未安装 TeX 发行版 | 跳过 PDF，仅生成 .tex 源；或用 Pandoc 导出 Word |
| 文风不符合导师要求 | 灰度测试时发现 | 调整 style_profile.md 或降级为 tier: flash |
| 篇幅被过度压缩 | scene 配置错误 | 确认 scene=report_review 而非 journal |
