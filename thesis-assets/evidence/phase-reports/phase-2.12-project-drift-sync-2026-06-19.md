# 阶段 2.12 项目漂移同步报告（2026-06-19）

## 1. 结论

已完成一次基于当时 `main` 的论文资产漂移审查。项目最近约 20 次提交显示：主线没有引入新的数据库表、API 路径或论文需要重绘的核心架构图；但质量门禁、BYOK 验证、语音 TTS 容错、报告任务幂等、消息序号一致性等工程证据已经明显领先旧论文资产，需要补充到 evidence 层。

本报告不修改 `chapters/*.md`，不启动阶段 3 正文改写。

> 后续补记：`57eba82` 之后，CI 又经历 BYOK/dark verify cold-start 稳定化、文档 whitespace 修复和本轮文档降噪整理。当前 active 证据入口以 `final-evidence-lock.md`、`quality-gates-2026-06-19.md`、`test-evidence-matrix-2026-06.md` 为准；本报告保留为过程记录。

## 2. 审查范围

本轮按当时 `main`，重点覆盖以下最近约 20 个主线提交或同域提交：

| 提交 | 类型 | 对论文资产影响 |
| --- | --- | --- |
| `57eba82` | BYOK 验证脚本 | 需记录 BYOK 浏览器验证 cold-start 稳定性与失败诊断能力 |
| `9ab9465` | 质量文档 | 需修正 sentrux 已接入 CI 的口径 |
| `6098ca0` | CI / JaCoCo | 需说明 `mvn test` 已触发 JaCoCo report，coverage 仍 report-only |
| `b821bf7` | CI / coverage / TTS 测试 | 需登记 Sentrux CI、JaCoCo artifact、TTS timeout 测试 |
| `1cd55d2` | TTS / fallback / audit | 需登记 TTS 30s timeout、双 flag、防止迟到 audio、npm audit 阻塞 |
| `588bf73` | 依赖 / fallback / seqNum | 需登记 audit 清零、fallback 过滤、`nextSeqNum` max+1 |
| `b39eee1` | 风险台账 / ReportJobWorker | 需登记残留风险台账与报告任务跳过路径测试 |
| `31272bc` | 依赖 / CI diff / mapping tests | 需登记 axios/markdown-it 升级、PR diff check、响应组装测试 |
| `e639d94` | 面试阶段消息 | 需登记系统消息统一走 `InterviewMessageService` |
| `5f0c786` | 报告任务幂等 | 需登记 `/finish` generating/finished 幂等保护 |
| `3894f31` | 前端验证门禁 | 需登记 `verify:byok`、`verify:dark`、whitespace gate 进入 CI 的历史起点 |
| `f67abc1` | 消息序号 | 需登记消息序号写入串行化 |
| `25ab32f` | 语音会话 | 需登记语音会话切换/不可用路径保护 |
| `30f845b` | UI token | 仅影响界面一致性，不触发正文事实变化 |
| `78dafb4` | UI token | 同上 |
| `0f6700d` | UI token | 同上 |
| `00ef462` | UI token | 同上 |
| `fbe74f6` | seed/dev fixture | 需保持 dev fixture 与历史 Demo 口径分离 |
| `b678729` | seed/dev fixture | 同上 |
| `c8b4177` | seed/dev fixture | 同上 |
| `41b27b1` | runtime | 确认 start-dev/start-docker 与 dev fixture 口径 |
| `51304d7` | runtime | 确认 Demo Twin 退役边界 |

## 3. 已补齐证据

| 新增/更新资产 | 处理内容 |
| --- | --- |
| `thesis-assets/evidence/test-data/quality-gates-2026-06-19.md` | 新增质量门禁与自动化验证证据快照，覆盖 Sentrux、JaCoCo、npm audit、BYOK verify、dark verify、recent refactor 边界 |
| `thesis-assets/meta/final-evidence-lock.md` | 将质量门禁证据纳入当前有效证据资产，并补充 2026-06-19 漂移同步边界 |
| `thesis-assets/evidence/figure-table-register.md` | 表 5.3 增补质量门禁证据路径 |
| `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 修正 Demo Twin、语音、BYOK/fallback、质量门禁相关功能用例口径 |

## 4. 其他论文资产状态审查

| 资产 | 状态判断 | 处理意见 |
| --- | --- | --- |
| `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` | 原长表大体可用，但历史 Demo Twin 主叙述过重 | 已在后续整理中压缩为当前证据矩阵 |
| `thesis-assets/evidence/test-data/dev-fixture-2026-06.md` | 已明确旧 Demo Twin 迁移说明 | 后续整理中进一步瘦身为 dev fixture 边界说明 |
| `thesis-assets/evidence/test-data/env-2026-06.md` | 环境与 BYOK/RabbitMQ 链路仍可用，但构建记录不是最新完整质量门禁 | 由质量门禁快照补足 |
| `thesis-assets/evidence/figure-table-register.md` | 图 3.3 对 MySQL/Redis/RabbitMQ/dev fixture 的边界说明仍匹配当前架构 | 无需重绘图 |
| `thesis-assets/defense/` | 答辩材料可能仍含历史 Demo 性能表达 | 后续整理中已对讲稿和 slide-map 做口径收缩 |
| `output/README.md` | 可作为运行产物说明，不应作为正文事实源 | 保留 |

## 5. 进入正文前的强制边界

1. 不得把旧 Demo Twin 时延写成当前 start-dev / start-docker 的性能数据。
2. 不得把 BYOK verify 脚本的 mock API 流程写成真实公网模型性能验证。
3. 不得把 TTS 单元测试写成真实 ASR/TTS 服务端到端低延迟测试。
4. 不得把 JaCoCo report-only 写成覆盖率阈值达标。
5. 不得把 Sentrux 当前有限规则写成完整架构正确性证明。
6. 不得把 RabbitMQ 本地链路写成生产级可靠投递。

## 6. 后续建议

- 阶段 3 前，继续以 active evidence 为事实入口，不再从早期阶段报告中抽取当前事实。
- 若答辩材料要直接使用，应先审查 `defense/script.md` 和 `defense/slide-map.md` 是否与当前 CI、运行入口和测试矩阵一致。
- 如果后续真的要写语音能力，应补一份真实 ASR/TTS 端到端测试记录；否则正文只能写架构与单元测试覆盖。
