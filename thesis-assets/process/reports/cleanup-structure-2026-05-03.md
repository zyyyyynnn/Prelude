# 结构整理报告（2026-05-03）

## 1. 移动文件

- `polish-source/*.sync.md`、`sync-decision-report.md`、`md-word-sync-report.md` 已移动到 `thesis-assets/process/sync/`。
- 中英文摘要和第一章至第六章分章草稿已移动到 `thesis-assets/drafts/`。
- 文献相关文件已移动到 `thesis-assets/literature/`。
- 图表、测试、代码片段和 Bug 证据已移动到 `thesis-assets/evidence/`。
- 答辩材料包、PPT 页级映射表和答辩讲稿已移动到 `thesis-assets/defense/`。
- cleanup、breakpoint、review、format-check、next-step、real-info、frontend-minimal、final-mvp 等过程报告已移动到 `thesis-assets/process/reports/`。
- `school-requirements-package.md` 和 `贵州大学毕业论文（设计）管理办法.pdf` 已移动到 `thesis-assets/archive/legacy-reports/`。

## 2. 重命名文件

- `thesis-assets/毕业论文正式版（草稿）副本.docx` 已重命名并移动为 `thesis-assets/current/毕业论文正式版（润色回填）.docx`。
- `thesis-assets/polish-source/thesis-polished.md` 已移动为 `thesis-assets/current/thesis-polished.md`。

## 3. 归档文件

- 学校要求包和管理办法 PDF 已归档到 `thesis-assets/archive/legacy-reports/`，作为学校要求核查依据保留。
- 过程报告已归档到 `thesis-assets/process/reports/`，只保留过程信息，不替代当前总控和证据锁版。

## 4. 删除情况

- 删除了移动后为空的 `thesis-assets/polish-source/` 目录。
- 未删除任何内容文件。

## 5. 缺失未恢复文件

- `thesis-assets/毕业论文资料整合.docx`：历史中已删除，当前不恢复；索引文档已改为缺失历史快照状态说明。
- `thesis-assets/贵州大学答辩PPT模板.pptx`：历史中已删除，当前不恢复；索引文档已改为需人工重新提供或从历史提交恢复。

## 6. docs/ 改动

- 保持 `docs/` 作为公开文档和 README 展示图目录。
- 未迁入论文过程材料。
- `docs/README.md` 已补充边界说明：论文过程报告、润色稿、格式检查报告和 `output/` 批量截图不放入 `docs/`。

## 7. thesis-handbook/ 改动

- 保持 `thesis-handbook/` 作为毕设流程手册目录。
- 更新 `thesis-handbook/README.md` 中学校要求包路径。
- 将 PPT 模板状态改为当前未入库，需人工重新提供或从历史提交恢复。

## 8. 当前主交付物路径

- 原始主稿 / 格式母版：`thesis-assets/毕业论文正式版（草稿）.docx`
- 当前 Word 工作稿：`thesis-assets/current/毕业论文正式版（润色回填）.docx`
- Markdown 润色基准稿：`thesis-assets/current/thesis-polished.md`

## 9. 后续 Word 格式检查入口

- 后续格式终审优先检查 `thesis-assets/current/毕业论文正式版（润色回填）.docx`。
- `thesis-assets/毕业论文正式版（草稿）.docx` 继续作为原始主稿和格式母版保留，不覆盖。

## 10. 仍需人工确认事项

- 封面和诚信责任书中的指导教师、签名、日期等人工字段。
- Word 目录域、页码、页眉页脚、图题表题和参考文献格式终审。
- PPT 模板是否人工重新提供或从历史提交恢复。
- 是否需要真实公网 LLM 性能测试。
