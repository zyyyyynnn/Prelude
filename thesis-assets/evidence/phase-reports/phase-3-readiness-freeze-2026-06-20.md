# 阶段 3 Readiness Freeze（2026-06-20）

## 文件定位

本文件记录阶段 3 执行准备的 Final Evidence Freeze、图表表格一致性核对、正文口径只读审查和答辩材料就绪核对结果。它是 evidence 层冻结说明，不是正文改写稿。

## 阶段 3 freeze 与后续同步基线

| 项 | 内容 |
| --- | --- |
| 冻结审查基线（原始） | `4b2e967d92b737d332b5651d102543137e6adba7` |
| 冻结记录提交 | `2bf27b728bd9f08f4b897f88ceb65a752d5a33e0` |
| 冻结审查基线提交信息 | `docs(thesis): compress historical phase 2.10 report` |
| CI 状态 | GitHub Actions CI run `27815679764` 在 `4b2e967` 上通过 |
| 当前同步基线 | `e8fa5378b9eab4cd2e2512b3844dbbed6c7f0827`（`origin/main` HEAD） |
| UI 同步说明 | UI semantic sizing 与 `verify:ui` UI drift guardrail 已进入 `main`（`verify:ui` 为 `frontend/package.json` 中 npm script，当前仅本地预检，未进 `.github/workflows/ci.yml`）；本同步只更新证据口径，不修改 `thesis-assets/chapters/*.md` 正文 |
| npm audit | `npm --prefix frontend audit --omit=dev` 返回 `found 0 vulnerabilities` |
| 正文处理 | 本轮未修改 `thesis-assets/chapters/*.md` |
| 冻结范围 | evidence、图表登记、质量门禁证据、答辩材料入口 |

## 本阶段证据入口

| 入口 | 路径 | 用途 |
| --- | --- | --- |
| 证据锁定索引 | `thesis-assets/meta/final-evidence-lock.md` | 当前有效证据入口 |
| 质量门禁证据 | `thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | CI、本地质量门禁、npm audit、BYOK/dark verify 边界 |
| 测试证据矩阵 | `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 测试数据与章节可写性映射 |
| 功能用例 | `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | TC-01 ~ TC-12 与功能边界 |
| 图表登记 | `thesis-assets/evidence/figure-table-register.md` | 图 3.x、表 5.x 与证据来源 |
| 图表准入说明 | `thesis-assets/evidence/figure-assets-guidance.md` | 图表、截图和可视化进入正文的准入条件 |
| 答辩讲稿 | `thesis-assets/defense/script.md` | 当前 5-8 分钟讲稿口径 |
| PPT 映射 | `thesis-assets/defense/slide-map.md` | 答辩页级证据映射 |

## 可写入正文的事实

- 当前运行入口为 `start-dev.bat` 与 `start-docker.bat`；旧 Demo Twin、`start-demo`、`start-real`、8081/5174 仅作为历史材料保留。
- `start-dev.bat` 服务于日常开发、人工验收和答辩演示；Docker 只启动 MySQL、Redis、RabbitMQ，本机运行后端和 Vite 前端。
- `start-docker.bat` 服务于 Full Docker / 部署验证，使用 Docker Compose 拉起应用与中间件。
- dev fixture 是 local/dev 本地验收辅助能力，不进入 Full Docker / prod 默认路径。
- RabbitMQ 已用于报告生成异步任务队列；可描述 `/finish -> generating -> RabbitMQ -> ReportJobWorker -> summary_report -> finished -> report_ready` 闭环。
- Redis 职责为限流、缓存、评分锁和状态辅助，不承担报告任务队列职责。
- BYOK 支持 OpenAI-compatible endpoint root、API Key、模型发现、配置保存、配置测试和链路复用；API Key 加密保存，前端不回显明文。
- 自动化质量门禁分两层：CI 包含 whitespace diff check、Sentrux、后端测试、JaCoCo report artifact、npm audit、前端 build、BYOK verify、dark verify；本地预检另包含 `verify:ui` UI 静态 guardrail（`verify:ui` 仅本地 npm script，未进 CI）。
- JaCoCo 是 report-only；Sentrux 是有限规则边界检查；BYOK verify 和 dark verify 是 CI 自动化流程 sanity check；`verify:ui` 是本地预检 UI 静态 guardrail 与 semantic sizing 红线扫描，不是全量视觉回归。

## 不可写入正文的夸大表述

- 不得写 RabbitMQ 已具备生产级可靠投递、消息零丢失、DLQ、outbox、publisher confirm 或消费并发调优能力。
- 不得写已完成公网高并发压测或复杂部署环境可靠性验证。
- 不得写真实 ASR/TTS 端到端低延迟性能已完成。
- 不得写 BYOK 对所有 OpenAI-compatible endpoint 兼容，或 openai-compatible 失败会无感切换到系统 provider。
- 不得写 JaCoCo 覆盖率已达标或 coverage threshold gate 已启用。
- 不得写 Sentrux 证明完整架构正确性。
- 不得写 UI 完全无缺陷；只能写 token/样式约束与自动化 verify 支撑关键路径 sanity check（`verify:ui` 只证明静态红线扫描通过）。

## 图表表格一致性结论

- `figure-table-register.md` 已覆盖当前图 3.1、图 3.2、图 3.3、候选图 4.x，以及第五章正文实际出现的表 5.1 到表 5.9。
- `figure-assets-guidance.md` 已与 register 对齐，质量门禁证据被标记为补充证据，不再误登记为当前正文编号表。
- `slide-map.md` 使用当前图 3.1、图 3.2、图 3.3 和候选图 4.x；旧 Demo Twin 只用于历史 Bug 复盘页，不作为当前架构或演示路线。

## 正文只读审查结果

本轮只读扫描 `thesis-assets/chapters/*.md`，未发现任务指定的旧运行入口、旧技术栈或夸大能力口径命中。

正文待处理清单：

- 暂无 P0/P1 旧口径问题。
- 第五章表 5.1 到表 5.9 已由 register/guidance 补齐证据登记；正文内容本轮未改。

## 仍保留的 P3 边界

- TTS 全局单线程执行器仍是吞吐观察项；当前只可写顺序保护、timeout 和容错测试，不写多用户语音吞吐能力。
- SSE 长连接未做大规模并发稳定性测试。
- Redis 限流未做高流量触发统计。
- RabbitMQ 未做生产级消息可靠性增强与复杂部署验证。
- BYOK 兼容性受第三方 endpoint 差异影响，不扩展为全平台兼容结论。

## 未修改正文说明

根据 `workflow-governance.md`，本轮阶段 3 准备只冻结 evidence 和答辩材料口径，不直接修改 `thesis-assets/chapters/*.md`。后续如需正文修订，应由用户和审查官确认后，按单章执行。
