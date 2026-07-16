# 论文证据层索引

本目录是项目实现与论文正文之间的证据层。当前事实和写作边界以 `../meta/final-evidence-lock.md` 为准；本文件只负责终态导航。

## 当前证据入口

| 类型 | 路径 | 用途 |
| --- | --- | --- |
| 证据与章节映射 | `test-data/test-evidence-matrix.md` | 第三至第五章可使用的测试与实现证据 |
| 功能用例 | `test-data/functional-cases-2026-07-15.md` | TC-01 至 TC-15 功能与边界验证 |
| 质量门禁 | `test-data/quality-gates-2026-07-15.md` | 本地、CI、覆盖率与质量门禁快照 |
| 测试环境 | `test-data/environment-2026-07-15.md` | 当前工具链、运行依赖与构建环境 |
| 数据库字典 | `test-data/database-table-dictionary-2026-07-15.md` | 当前表结构和 E-R 图字段依据 |
| dev fixture | `test-data/dev-fixture-2026-07-15.md` | local/dev 验收数据和运行边界 |
| 检索容量 | `test-data/retrieval-capacity-2026-07-15.md` | 合成数据容量快照与适用边界 |
| 实现证据 | `code-snippets/evidence-driven-training-and-safety-2026-07-15.md` | 简历闭环、BYOK、安全、检索与作业恢复 |
| 图表登记 | `figure-table-register.md` | 图表、测试表格和准入状态 |
| 图表文件 | `diagrams/` | Mermaid 事实源及 PNG/SVG 导出图 |

## 目录职责

| 路径 | 当前内容 | 归档位置 |
| --- | --- | --- |
| `code-snippets/` | 可直接回溯到当前源码的实现证据 | `code-snippets/archive/` |
| `test-data/` | 当前环境、功能、质量、容量和结构化证据 | `test-data/archive/` |
| `diagrams/` | 当前图表源文件与导出文件 | 通过 Git 历史追溯旧版本 |
| `bug-evidence/` | 工程问题复盘归档 | `bug-evidence/archive/` |
| `phase-reports/` | 历史阶段报告索引 | `phase-reports/archive/` |

## 图表准入

- Mermaid `.mmd` 是工程图的事实源，PNG/SVG 是同步导出产物。
- 图表必须能回溯到源码、DDL、接口时序、真实截图或测试数据。
- 图表进入正文或答辩材料前必须登记，并由用户和审查官复核。
- UI 截图使用当前真实页面，稳定展示图存放于 `docs/images/`。
- 未采集的数据不得制作成性能、准确率或可靠性图表。

## 归档规则

- 各子目录的归档文件只用于演进追溯，不作为当前事实入口。
- 旧路径、旧类名、旧运行模式和旧测试口径保留在归档文件中，不向当前索引回流。
- 历史结论需要重新使用时，必须先与当前源码和 `final-evidence-lock.md` 核对。
