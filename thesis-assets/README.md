# Prelude 论文资产索引

`thesis-assets/` 保存论文正文、证据、文献、学校规范和答辩材料。项目开发文档位于 `docs/`，两者不混放。

## 权威入口

| 优先级 | 文件 | 职责 |
| --- | --- | --- |
| 1 | `meta/workflow-governance.md` | 论文工作流、阶段准入和操作红线 |
| 2 | `meta/final-evidence-lock.md` | 当前实现基线、有效证据和写作边界 |
| 3 | `evidence/README.md` | 证据目录、当前证据与归档导航 |
| 4 | `chapters/*.md` | 论文正文唯一真相源 |

## 目录结构

| 路径 | 内容 | 维护原则 |
| --- | --- | --- |
| `official/` | 学校格式要求和工作指南 | 只保存权威原件或忠实整理稿 |
| `meta/` | 治理规范、证据状态索引、Word 模板 | 只保留全局规则和状态入口 |
| `chapters/` | 摘要、第一至第六章正文 | 正文修改只发生在此目录 |
| `evidence/` | 代码、测试、图表和历史过程证据 | 当前证据与历史归档分层保存 |
| `literature/` | 文献主库、质量复核、证据映射和最终编号 | 文献进入正文前必须完成核验 |
| `defense/` | 答辩讲稿和页级映射 | 与当前正文和证据口径保持一致 |

## 正文顺序

1. `chapters/abstract-keywords.md`
2. `chapters/chapter-01-introduction.md`
3. `chapters/chapter-02-related-tech.md`
4. `chapters/chapter-03-analysis-design.md`
5. `chapters/chapter-04-implementation.md`
6. `chapters/chapter-05-testing.md`
7. `chapters/chapter-06-conclusion.md`

## 资产边界

- 当前事实以 `meta/final-evidence-lock.md` 为准，历史过程记录不能覆盖当前口径。
- 图表必须登记在 `evidence/figure-table-register.md` 后才能进入正文或答辩材料。
- 阶段推进必须提交差异并经用户和审查官复核。
- 提交版 DOCX/PDF 由 Word/WPS 人工终审产生，不作为正文修改源。
