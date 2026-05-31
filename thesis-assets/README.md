# thesis-assets 论文材料目录

本目录用于存放毕业论文当前交付物、论文证据、文献材料、测试记录、答辩准备文件和过程归档。当前状态判断以本文件、`thesis-control.md` 和 `final-evidence-lock.md` 为准。

## 当前主交付物

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 原始主稿 / 格式母版 | `毕业论文正式版（草稿）.docx` | 原始主稿继续保留，不覆盖；用于保留 Word 结构、封面、诚信责任书和格式母版 |
| 当前 Word 工作稿 | `current/毕业论文正式版（润色回填）.docx` | 已完成 `current/thesis-polished.md` 到 Word 母版的真实回填；后续格式终审优先检查此文件 |
| Markdown 润色基准稿 | `current/thesis-polished.md` | 当前内容定版后的 Markdown 润色基准稿 |
| 总控 | `thesis-control.md` | 当前状态唯一总控；状态冲突时优先参考本文件和 `final-evidence-lock.md` |
| 证据锁版 | `final-evidence-lock.md` | 记录截图、图表、测试、代码、文献和答辩证据是否同步 |
| 补料状态 | `final-supplement-checklist.md` | 记录终稿前已完成、仍需人工终审、预留待测和可选补充内容 |
| 资料清单 | `material-checklist.md` | 五类资料包状态：学校要求、文献、素材、Bug、答辩 |

## 目录结构

| 目录 | 内容 | 维护规则 |
| --- | --- | --- |
| `current/` | 当前 Word 工作稿和 Markdown 润色基准稿 | 后续人工格式终审和内容核对优先从这里进入 |
| `drafts/` | 中英文摘要与第一章至第六章原始分章草稿 | 作为分章历史草稿保留，不替代 `current/` |
| `process/sync/` | Word/Markdown 同步报告、同步稿和同步决策报告 | 用于追溯同步过程，不作为正文直接入口 |
| `process/reports/` | cleanup、breakpoint、review、next-step、format-check 等过程报告 | 只保留过程信息；若与总控冲突，以当前索引为准 |
| `literature/` | 参考文献库、文献质量评估、证据映射和官方来源登记 | 正式文献状态以 `references-draft.bib` 和质量评估为准 |
| `evidence/` | 图表、测试、代码片段和 Bug 证据 | 新增证据后同步更新 `final-evidence-lock.md` |
| `defense/` | 答辩材料包、PPT 页级映射表和讲稿 | PPT 模板当前未入库，需人工重新提供或从历史提交恢复 |
| `archive/legacy-reports/` | 学校要求包、管理办法 PDF 和历史要求材料 | 作为核查依据保留，不作为当前交付物 |

## 关键路径

| 材料 | 路径 |
| --- | --- |
| 分章草稿 | `drafts/abstract-keywords.md`、`drafts/chapter-01-introduction-draft.md` 至 `drafts/chapter-06-conclusion-abstract-draft.md` |
| 同步稿 | `process/sync/abstract-keywords.sync.md`、`process/sync/chapter-01.sync.md` 至 `process/sync/chapter-06.sync.md` |
| 同步报告 | `process/sync/md-word-sync-report.md`、`process/sync/sync-decision-report.md` |
| 图表登记 | `evidence/figure-table-register.md` |
| 图表源文件与 PNG | `evidence/diagrams/` |
| 测试证据 | `evidence/test-data/` |
| 核心代码证据 | `evidence/code-snippets/` |
| Bug 证据 | `evidence/bug-package-2026-04-24.md`、`evidence/bug-evidence/` |
| 文献工作库 | `literature/references-draft.bib` |
| 文献质量与映射 | `literature/literature-quality-review.md`、`literature/literature-evidence-map.md` |
| 文献补充材料 | `literature/citation-placement-plan.md`、`literature/official-network-references-draft.md`、`literature/official-source-register.md` |
| 学校要求归档 | `archive/legacy-reports/school-requirements-package.md`、`archive/legacy-reports/贵州大学毕业论文（设计）管理办法.pdf` |
| 答辩材料 | `defense/defense-package-2026-04-25.md`、`defense/defense-slide-map.md`、`defense/defense-script-5-8min.md` |

## 缺失或未恢复材料

| 文件 | 当前状态 |
| --- | --- |
| `毕业论文资料整合.docx` | 历史中已删除，当前不恢复；不再作为当前状态依据 |
| `贵州大学答辩PPT模板.pptx` | 当前未入库；如需使用，需人工重新提供或从历史提交恢复到 `thesis-assets/defense/贵州大学答辩PPT模板.pptx` |

## 放置规则

- `毕业论文正式版（草稿）.docx` 是原始主稿和格式母版，不覆盖。
- `current/毕业论文正式版（润色回填）.docx` 是当前 Word 工作稿，已完成 Markdown 润色基准稿真实回填，后续格式终审优先检查。
- `current/thesis-polished.md` 是当前 Markdown 润色基准稿。
- `docs/` 只放项目公开文档和 README 展示图，不放论文过程材料。
- `thesis-handbook/` 只放毕设流程手册和模板，不放实际论文产物。
- Demo Twin 测试数据只代表本机回环演示环境，不代表真实公网 LLM 性能。
- 新增截图、图表、测试或代码证据后，应同步更新 `final-evidence-lock.md`。
- 新增或修改图表编号时，统一使用 `图3.1`、`表5.1` 点号格式，不使用空格或连字符格式。

## 当前阶段

- 当前阶段：Word 格式终审与答辩 PPT 制作。
- 当前卡点：封面和诚信责任书中的指导教师、签名、日期等人工字段仍需确认；Word 目录域更新、页码复核、页眉页脚复核、图题表题格式复核和参考文献格式复核仍需终审。
- 真实公网 LLM 性能测试已于 2026-05-27 完成（MIMO mimo-v2.5-pro，TC-01 至 TC-11 全部通过），见 `evidence/test-data/mimo-real-test-2026-05-27.md`。
