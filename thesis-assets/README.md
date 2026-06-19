# thesis-assets 论文资产索引

## 1. 核心原则

* `chapters/*.md` 是论文正文唯一真相源。
* `meta/workflow-governance.md` 是论文资产治理最高规范。
* `meta/gzu-thesis-template.docx` 是唯一 active Word 格式模板。
* `official/` 存放学校权威格式要求与工作指南。
* Word / DOCX / PDF 不得作为正文来源。
* 所有阶段必须经用户和审查官复核后推进。

## 2. 当前有效目录

| 路径            | 职责                                                     | 是否 active |
| ------------- | ------------------------------------------------------ | --------- |
| `official/`   | 学校格式要求、工作指南等权威源                                        | 是         |
| `meta/`       | 治理规范、证据锁定索引、唯一模板                                       | 是         |
| `chapters/`   | 正文 Markdown 唯一真相源                                      | 是         |
| `evidence/`   | 图表、测试、截图、代码片段等证据                                       | 是         |
| `evidence/phase-reports/` | 过程记录，仅用于审计和追溯，不直接作为正文事实依据 | 降权保留 |
| `literature/` | `references.bib`、`quality-review.md`、`evidence-map.md` | 是         |
| `defense/`    | 答辩材料                                                   | 是         |

## 3. 权威格式基线

| 文件                                    | 角色                  |
| ------------------------------------- | ------------------- |
| `official/gzu-format-requirements.md` | 学校格式要求整理稿           |
| `official/gzu-thesis-work-guide.doc`  | 学校工作指南原件            |
| `meta/gzu-thesis-template.docx`       | 唯一 active Word 格式模板 |

## 4. 当前流程入口

* 治理规范：`meta/workflow-governance.md`
* 证据锁定：`meta/final-evidence-lock.md`
* 测试事实矩阵：`evidence/test-data/test-evidence-matrix-2026-06.md`
* 质量门禁证据：`evidence/test-data/quality-gates-2026-06-19.md`
* 文献资产：`literature/references.bib`、`literature/quality-review.md`、`literature/evidence-map.md`
* 图表证据：`evidence/figure-table-register.md`
* 过程记录：`evidence/phase-reports/README.md`，仅用于审计和追溯，不直接作为正文事实依据
* 最新项目漂移同步报告：`evidence/phase-reports/phase-2.12-project-drift-sync-2026-06-19.md`
* 正文唯一真相源：`chapters/*.md`

## 5. 构建边界

`build-docx.ps1` 只允许在图表、证据、文献、正文、引用全部冻结后运行。
构建产生的工作稿只作为临时输出，不作为正文来源。
提交版 DOCX/PDF 只能由 Word/WPS 人工终审后产生。
