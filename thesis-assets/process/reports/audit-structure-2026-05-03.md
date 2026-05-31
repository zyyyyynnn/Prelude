# 结构审计报告（2026-05-03）

## 审计输入

- `git status --short`：执行前工作区干净。
- `git ls-files docs thesis-handbook thesis-assets`：确认当前跟踪的公开文档、手册和论文材料文件。
- Word 文件 SHA256：
  - `thesis-assets/毕业论文正式版（草稿）.docx`：`CD304BB0BACB124326AF48163D64D95A10C92ABFCE48011B2ED73E5DBB1D1790`
  - `thesis-assets/毕业论文正式版（草稿）副本.docx`：`CD304BB0BACB124326AF48163D64D95A10C92ABFCE48011B2ED73E5DBB1D1790`

## 当前目录状态

### thesis-assets

- 根目录下混放论文主稿、Word 副本、分章草稿、同步报告、润色稿目录、文献材料、图表证据、测试证据、答辩材料、阶段报告和学校要求材料。
- `polish-source/` 当前包含 `thesis-polished.md`、7 个 `*.sync.md` 和 `sync-decision-report.md`。
- `毕业论文正式版（草稿）副本.docx` 当前存在且被跟踪，按用户更正确认为润色回填版，不作为无用副本删除。

### docs

- 当前包含 `README.md`、`api.md`、`images/`。
- 目录职责清晰，应继续作为公开文档和 README 展示图目录，不迁入论文过程材料。

### thesis-handbook

- 当前包含流程手册正文和 `assets-template/`。
- 目录职责清晰，应继续作为毕设流程手册目录，只更新旧路径和当前材料状态。

## 建议保留位置

- `thesis-assets/README.md`
- `thesis-assets/thesis-control.md`
- `thesis-assets/final-evidence-lock.md`
- `thesis-assets/final-supplement-checklist.md`
- `thesis-assets/material-checklist.md`
- `thesis-assets/毕业论文正式版（草稿）.docx`
- `docs/README.md`
- `docs/api.md`
- `docs/images/`
- `thesis-handbook/README.md`
- `thesis-handbook/*.md`
- `thesis-handbook/assets-template/`

## 建议移动位置

- `thesis-assets/毕业论文正式版（草稿）副本.docx` -> `thesis-assets/current/毕业论文正式版（润色回填）.docx`
- `thesis-assets/polish-source/thesis-polished.md` -> `thesis-assets/current/thesis-polished.md`
- `thesis-assets/polish-source/*.sync.md`、`sync-decision-report.md`、`md-word-sync-report.md` -> `thesis-assets/process/sync/`
- `abstract-keywords.md`、`chapter-*-draft.md` -> `thesis-assets/drafts/`
- 文献相关文件 -> `thesis-assets/literature/`
- 图表、测试、代码、Bug 证据 -> `thesis-assets/evidence/`
- 答辩材料 -> `thesis-assets/defense/`
- cleanup、breakpoint、review、format-check、next-step、real-info、frontend-minimal、final-mvp 等过程报告 -> `thesis-assets/process/reports/`
- `school-requirements-package.md`、`贵州大学毕业论文（设计）管理办法.pdf` -> `thesis-assets/archive/legacy-reports/`

## 删除候选

- `thesis-assets/polish-source/`：文件全部移动后为空目录，可删除空目录。
- 本轮无内容文件删除候选。

## 缺失但被索引引用的文件

- `thesis-assets/毕业论文资料整合.docx`：历史中已删除，当前不恢复；索引应改为缺失历史快照状态说明。
- `thesis-assets/贵州大学答辩PPT模板.pptx`：历史中已删除，当前不恢复；索引应改为需人工重新提供或从历史提交恢复。

## 需要人工确认的文件

- `thesis-assets/current/毕业论文正式版（润色回填）.docx`：作为当前 Word 工作稿，后续格式终审优先检查。
- `thesis-assets/毕业论文正式版（草稿）.docx`：继续作为原始主稿和格式母版，不覆盖。
- PPT 模板：当前未入库，需人工重新提供或从历史提交恢复。
