# 当前论文证据锁定索引

## 1. 文件定位

* 本文件只登记当前仍有效、可追溯、可进入后续阶段审查的论文证据资产。
* 本文件不登记历史草稿、临时构建产物或阶段性过程报告。
* 若与其他资产说明冲突，以 `workflow-governance.md` 为准。

## 2. 当前有效证据资产

| 证据类型      | 当前路径                                              | 用途          | 当前状态  | 限制说明                        |
| --------- | ------------------------------------------------- | ----------- | ----- | --------------------------- |
| 参考文献主库    | `thesis-assets/literature/references.bib`         | 参考文献数据库     | 已收口   | 由 Zotero / Better BibTeX 管理 |
| 文献质量复核表   | `thesis-assets/literature/quality-review.md`      | 文献质量审查依据    | 已收口   | 不冻结正文引用编号                   |
| 文献证据映射表   | `thesis-assets/literature/evidence-map.md`        | 文献到章节的证据映射  | 已收口   | 不等同于正文引用顺序                  |
| 图表登记表     | `thesis-assets/evidence/figure-table-register.md` | 图表资产索引      | 已更新   | 图表进入正文前必须登记；候选图4.x 未冻结图号   |
| 绘图与模型资产   | `thesis-assets/evidence/diagrams/`                | 图表源文件与导出图   | 可用    | 需与正文图题一致；含候选图4.x SSE流程图     |
| 测试数据与报告   | `thesis-assets/evidence/test-data/`               | 系统测试依据      | 可用    | 以 2026-06 版本为准；4 月数据已归档      |
| 测试证据矩阵   | `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 测试数据与论证证据映射 | 可用    | 映射所有证据到章节                   |
| 数据库表字典   | `thesis-assets/evidence/test-data/database-table-dictionary-2026-06.md` | 数据库表结构参考 | 可用    | 补充 E-R 图字段细节                 |
| 代码片段证据    | `thesis-assets/evidence/code-snippets/`           | 系统实现依据      | 可用    | 证据 4（正则评分）已废弃；以证据 9 为准      |
| Bug 与修复证据 | `thesis-assets/evidence/bug-evidence/`            | 问题复盘与答辩依据   | 可用    | 不夸大为系统能力证明                  |
| 阶段报告      | `thesis-assets/evidence/phase-reports/`            | 阶段审查记录      | 可用    | 2.10~2.11C-Fix 共 5 份       |
| 答辩材料      | `thesis-assets/defense/`                          | PPT、讲稿、答辩映射 | 可用    | 2026-06 口径重写版               |

## 3. 当前锁定边界

* 当前证据索引只确认资产位置与用途，不代表正文已经完成同步。
* 新增证据必须先进入当前有效证据路径，再由用户和审查官复核。
* 不允许通过临时输出或外部生成物反向覆盖 `chapters/*.md`。
* 文献资产以 `references.bib`、`quality-review.md`、`evidence-map.md` 为准。

## 4. 阶段状态

* 阶段 3 仍未开始。
* 正文未修改。
* 引用编号未冻结。
* DOCX/PDF 未生成。

## 5. RabbitMQ / MQ 口径限制

* 当前证据索引不得将 RabbitMQ / MQ 削峰解耦写成已实现系统能力。
* 当前系统使用 Redis List 作为轻量任务队列实现异步报告生成，未引入 RabbitMQ。
* 若后续需要纳入 RabbitMQ，必须先补齐后端 AMQP 依赖、docker-compose 服务、生产者/消费者代码、任务状态与测试证据。
