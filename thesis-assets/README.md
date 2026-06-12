# thesis-assets 论文资产索引

## 1. 核心原则

- `chapters/*.md` 是论文正文唯一真相源。
- `meta/workflow-governance.md` 是论文资产治理最高规范。
- `meta/gzu-thesis-template.docx` 是唯一 active Word 格式模板。
- `official/` 存放学校权威格式要求与工作指南。
- Word / DOCX / PDF 不得作为正文来源。
- 所有阶段必须经用户和审查官复核后推进。

## 2. 当前目录职责

| 路径 | 职责 | 是否 active |
| --- | --- | --- |
| `official/` | 学校格式要求、工作指南等权威源 | 是 |
| `meta/` | 治理规范、材料清单、构建配置、唯一模板 | 是 |
| `chapters/` | 正文 Markdown 唯一真相源 | 是 |
| `evidence/` | 图表、测试、截图、代码片段等证据 | 是 |
| `literature/` | 文献、Deep Research、参考文献翻新 | 是 |
| `defense/` | 答辩材料 | 是 |
| `archive/` | 历史只读归档 | 否 |
| `current/` | 自动构建工作稿输出目录 | 否 |

## 3. 权威格式基线

| 文件 | 角色 |
| --- | --- |
| `official/gzu-format-requirements.md` | 学校格式要求整理稿 |
| `official/gzu-thesis-work-guide.doc` | 学校工作指南原件 |
| `meta/gzu-thesis-template.docx` | 唯一 active Word 格式模板 |

## 4. 当前禁止使用的旧资产

以下类型不得作为当前流程入口：

- `thesis-final.docx`
- `school-template.docx`
- `school-template.cleaned.docx`
- `gzu-undergraduate-thesis-template.cleaned.docx`
- `毕业论文正式版（草稿）.docx`
- `毕业论文正式版（润色回填）.docx`
- `thesis-polished.md`
- `thesis-control.md`
- `paperspine-execution-plan.md`
- `process-reports/`
- `sync-merged/`

如在 archive/ 中出现，仅视作历史只读资料。

## 5. DOCX 工作稿说明

`build-docx.ps1` 只允许在图表、证据、文献、正文、引用全部冻结后运行。  
脚本输出仅为 `current/thesis-working-draft.docx` 工作稿，不是提交版。  
提交版 DOCX/PDF 只能由 Word/WPS 人工终审后产生。

## 6. 下一步流程入口

- 研究与引用：`literature/reference-refresh-plan.md`
- 图表资产：`evidence/figure-assets-plan.md`
- 证据锁定：`meta/final-evidence-lock.md`
- 治理规范：`meta/workflow-governance.md`
