# PaperSpine × thesis-assets 标准自动化流水线 (SOP)

> 最后更新：2026-06-06
> 状态：生产环境标准流程

---

## 核心纪律

1. **单章执行，不合并**：每一章作为独立的 `rewrite_existing` 任务，避免 64KB 全文导致 writing_rationale_matrix 过载和上下文截断。
2. **场景锁定**：使用 `scene=report_review`，不用 `journal`，毕业论文必须保留长篇幅与系统实现细节。
3. **证据强关联**：处理“系统实现”或“算法分析”等硬核章节前，确保 `evidence/` 下有充足的代码片段和截图支撑，否则 AI 引擎会以“缺乏证据支撑”为由裁减逻辑。
4. **纯物理终稿**：单章输出提取回 `chapters/` 后即为内容终稿。终稿阶段仅做物理拼接和 Pandoc 格式转换，**严禁将合并后的全文再次送入 PaperSpine 重写**。

---

## 第一阶段：分章节独立执行 (PaperSpine 引擎)

### 执行顺序与关注点

| 顺序 | 章节 | draft_path | 特殊注意事项 |
|------|------|-----------|-------------|
| 1 | 绪论 | `chapters/chapter-01-introduction.md` | 验证文风与排版基调 |
| 2 | 相关技术 | `chapters/chapter-02-related-tech.md` | 技术描述不得过度压缩 |
| 3 | 分析设计 | `chapters/chapter-03-analysis-design.md` | 严防图表引用丢失 |
| 4 | 系统实现 | `chapters/chapter-04-implementation.md` | ⚠️ 高危：必须确保 evidence 充足 |
| 5 | 系统测试 | `chapters/chapter-05-testing.md` | 保留测试用例与量化表格 |
| 6 | 总结展望 | `chapters/chapter-06-conclusion.md` | 篇幅较短，注意逻辑闭环 |
| 7 | 摘要 | `chapters/abstract-keywords.md` | 最后处理，确保高度精炼 |

### 1.1 PaperSpine 核心配置模板 (JSON)

每处理一章，需严格使用以下配置模板：

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

### 1.2 产物防覆写提取脚本（必做项）

PaperSpine 的 `paper_rewriting_output/` 是硬编码目录。**每章引擎跑完通过人工验收后，必须立即执行以下提取命令：**

```powershell
# 1. 提取重写后的章节内容到 chapters/（覆盖原稿，成为新版真相源）
Copy-Item paper_rewriting_output\final_paper\latex_report.md chapters\chapter-XX-xxx.md -Force

# 2. 备份逻辑矩阵与完整性审计报告
New-Item -ItemType Directory -Force -Path archive\matrices | Out-Null
Copy-Item paper_rewriting_output\writing_rationale_matrix.md archive\matrices\matrix-chapXX.md -Force
Copy-Item paper_rewriting_output\integrity_audit.md archive\matrices\audit-chapXX.md -Force
```

---

## 第二阶段：终稿物理构建 (Pandoc 渲染流)

### 2.1 文件组装

`build-docx.ps1` 内部会完成该步骤，自动将所有分章无缝拼接为 `thesis-full.md`。用户不应再手工执行拼接脚本。

### 2.2 Pandoc 导出 Word (含目录域)

执行以下命令直接生成带完整目录与排版样式的 `thesis-final.docx`：

```powershell
pwsh -ExecutionPolicy Bypass -File .\build-docx.ps1
```
*(注：手工 Pandoc 命令仅用于排查，不作为推荐入口。正式入口以 build-docx.ps1 为准。)*

### 2.3 “最后 5%”的人工收尾清单

Pandoc 输出后，需手工在 Word 中完成以下收尾动作：
- [ ] 封面页信息（导师姓名、签名、日期）
- [ ] 诚信责任书亲笔签名
- [ ] 目录域更新刷新（`Ctrl+A` 全选 -> 按 `F9` 键）
- [ ] 页眉、页脚及底端页码的学校格式对齐
- [ ] 图题、表题手工微调
- [ ] 基于 GB/T 7714 的规范化参考文献列表粘贴

---

## 异常熔断与风险预案

| 异常表现 | 根本原因 / 触发条件 | 抢修指令 |
|----------|-------------------|----------|
| **核心实现细节被 AI 大幅删减** | `evidence/` 目录中缺乏直接对应的日志或代码支撑 | 撤回本章修改；在 `evidence/` 补足截图或代码后重新走 `rewrite_existing` |
| **引用标号 `[N]` 发生错乱或消失** | 大模型执行长文本总结时结构降维 | 检查 JSON 中 `special_requirements` 是否漏传了“保留原始 [N] 编号体系”的指令 |
| **Integrity Audit 爆红 (BLOCKED)** | 论文文本中出现了没有任何参考资料支撑的虚假主张 | 视情况调整主张语气，或通过 `citation` 库补充底层参考文献 |
| **目录/排版全损丢失** | 遗漏 Pandoc 关键参数 | 确认 Pandoc 渲染时是否携带了 `--toc` 与 `--reference-doc` |
