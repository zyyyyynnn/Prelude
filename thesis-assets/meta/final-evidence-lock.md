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
| 阶段过程记录      | `thesis-assets/evidence/phase-reports/`            | 审计和追溯      | 可追溯    | 不直接作为正文事实依据       |
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

* 代码层面，报告生成异步任务队列已由 RabbitMQ 承担；`/finish` 接口将 `session.status` 置为 `generating` 并通过 `RabbitTemplate.convertAndSend(REPORT_EXCHANGE, REPORT_ROUTING_KEY, ReportJobMessage)` 发布任务；`ReportJobWorker` 通过 `@RabbitListener(queues = REPORT_QUEUE)` 消费并在完成后通过 SSE 推送 `report_ready` 事件。
* 本地 Docker Compose 环境下已通过 `mvn -q test`（22/22）+ `docker compose config --quiet` + `prelude-rabbitmq` 健康检查 + `/finish` → RabbitMQ → `report_ready` 端到端基础链路联调。
* OpenAI-compatible BYOK 已支持用户级 endpoint、API Key、模型发现与运行模型选择；API Key 加密保存。2026-06-14 已在 Docker Compose 容器环境关闭 Demo 模式，通过用户级 BYOK 完成模型发现、配置保存、配置测试，并完成一次 `/finish` → RabbitMQ → `ReportJobWorker` → 真实 LLM 调用 → `report_ready` 功能链路验证；具体模型仅为运行参数，不作为仓库默认配置或论文模型推荐依据。
* Redis 回归限流、缓存和状态辅助职责；本轮仍保留 `spring-boot-starter-data-redis`。
* 严格限制：上述验证仅覆盖本地 Docker Compose 基础链路与一次真实 API Key 功能链路，**不等同于公网高并发压测**，**不证明生产级可靠投递**，**不证明消息绝不丢失**。
* 数据库源 DDL 已同步 `interview_session.status = ongoing / generating / finished`，其中 `generating` 对应 RabbitMQ 报告任务已发布但尚未完成消费的中间态。
* 当前实现未引入 DLQ、outbox、publisher confirm、消费并发调优。
* 答辩材料与正文如要使用“已引入 RabbitMQ”作为可写能力宣称，必须同时保留上述严格限制段。
