# thesis-assets 论文资产索引

## 核心原则

- `chapters/*.md` 是论文正文唯一真相源，本轮整理不修改正文。
- `meta/workflow-governance.md` 是论文资产治理最高规范。
- `meta/final-evidence-lock.md` 只登记当前有效证据入口，不承载长篇过程说明。
- Word / DOCX / PDF 不得作为正文来源。
- 阶段推进必须经用户和审查官复核。

## 当前有效目录

| 路径 | 职责 | 当前状态 |
| --- | --- | --- |
| `official/` | 学校格式要求、工作指南等权威源 | active |
| `meta/` | 治理规范、证据锁定索引、唯一 Word 模板 | active |
| `chapters/` | 正文 Markdown 唯一真相源 | active / 本轮不触碰 |
| `evidence/diagrams/` | 图表源文件与导出图 | active |
| `evidence/test-data/` | 测试、环境、质量门禁和证据矩阵 | active |
| `evidence/code-snippets/` | 可回溯到源码的实现证据 | active |
| `evidence/bug-evidence/` | 精选问题复盘证据 | active / 只保留可直接引用项 |
| `evidence/phase-reports/` | 阶段过程记录 | archived trace / 不直接作为正文事实依据 |
| `literature/` | `references.bib`、文献质量复核和证据映射 | active |
| `defense/` | 答辩讲稿与 PPT 映射 | active |

## 当前流程入口

| 入口 | 路径 | 用途 |
| --- | --- | --- |
| 治理规范 | `meta/workflow-governance.md` | 阶段、红线和工具边界 |
| 证据锁定 | `meta/final-evidence-lock.md` | 当前有效证据索引 |
| 最新漂移同步 | `evidence/phase-reports/phase-2.13-modular-monolith-sync-2026-07-13.md` | 两轮重构的证据、图表与正文影响复核入口 |
| 阶段 3 准备冻结 | `evidence/phase-reports/phase-3-readiness-freeze-2026-06-20.md` | Final Evidence Freeze、图表表格一致性和答辩材料核对 |
| 测试证据矩阵 | `evidence/test-data/test-evidence-matrix-2026-06.md` | 章节可写性与测试边界 |
| 质量门禁证据 | `evidence/test-data/quality-gates-2026-07-13.md` | 两轮重构后的 CI / 本地质量门禁候选快照 |
| 功能用例 | `evidence/test-data/functional-cases-2026-06.md` | TC-01 ~ TC-12 功能边界 |
| 图表登记 | `evidence/figure-table-register.md` | 图表与表格资产索引 |
| 文献资产 | `literature/references.bib`、`literature/quality-review.md`、`literature/evidence-map.md` | 文献依据 |
| 答辩资产 | `defense/script.md`、`defense/slide-map.md` | 当前答辩口径 |

## 历史材料处理规则

- 旧 Demo Twin、`start-demo`、`start-real`、8081/5174 等材料只作为历史对照或归档追溯，不作为当前运行入口。
- 阶段报告只回答“当时做过什么”，不覆盖当前证据事实。
- 若历史文件与当前 active 证据冲突，以 `final-evidence-lock.md` 和对应 active evidence 文件为准。

## 构建边界

`build-docx.ps1` 只允许在图表、证据、文献、正文、引用全部冻结后运行。构建产物只作为临时工作稿，不作为正文来源；提交版 DOCX/PDF 只能由 Word/WPS 人工终审后产生。
