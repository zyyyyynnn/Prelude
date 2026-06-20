# 质量门禁与自动化验证证据快照（2026-06-19）

## 文件定位

本文件是论文第五章“构建与自动化验证记录”的补充证据。

- 对应位置：第五章构建与自动化验证补充证据；当前正文未单列“构建与自动化验证记录表”。
- 证据性质：工程质量门禁与自动化验证链路记录。
- 适用边界：证明当前项目具备可重复的本地/CI 验证基础，不证明生产环境高并发性能、模型质量稳定性或消息队列零丢失。

## 当前基线

| 项 | 内容 |
| --- | --- |
| 当前同步基线（`origin/main` HEAD） | `e8fa5378b9eab4cd2e2512b3844dbbed6c7f0827` |
| 代码行为事实基线（最近 CI 通过） | `4b2e967`（CI run `27815679764` 已通过） |
| 文档收口基线 | `4b2e967` 为阶段 3 原始 freeze 审查基线；当前 active evidence 入口同步基线为 `e8fa5378`（见 `final-evidence-lock.md`） |
| 最近治理范围 | 约最近 20 次提交，覆盖 UI token 收敛、UI semantic sizing 与 drift guardrail、dev fixture 收敛、语音链路硬化、消息序号一致性、报告任务幂等、BYOK 验证、Sentrux/JaCoCo/npm audit 门禁，以及阶段 2 历史文档降噪收口 |
| 运行入口口径 | `start-dev` + `start-docker`；旧 Demo Twin / start-real / start-demo 均为历史状态 |
| npm audit | `npm --prefix frontend audit --omit=dev` 返回 `found 0 vulnerabilities` |
| BYOK / dark verify | 已进入 CI 和本地质量门禁，并完成 cold-start 等待与失败诊断加固 |
| UI guardrail | `verify:ui`（UI 静态扫描与 semantic sizing 红线）已进入 main；当前为前端 npm script 形态 |
| 论文状态 | 阶段 3 执行准备仅冻结 evidence 与答辩材料口径，不修改 `chapters/*.md`；正文修订仍需用户和审查官确认 |

## CI 与本地质量门禁

| 门禁项 | 当前实现 | 可支撑结论 | 限制 |
| --- | --- | --- | --- |
| whitespace diff check | push 使用 `HEAD~1`，PR 使用 `base.sha...HEAD` | 可阻断新增 whitespace 错误 | 不等同全量格式化 |
| Sentrux 架构规则 | CI 下载固定版本并校验 SHA256，执行 `sentrux check .` | 架构边界规则进入 CI | 当前规则数量有限 |
| 后端测试 | `mvn -q test`；JaCoCo report 绑定 test phase | 单元测试通过并生成覆盖率报告 | coverage report-only，无阈值 |
| JaCoCo artifact | CI 上传 `backend-jacoco-report` | 可供审查覆盖率明细 | 不阻塞构建 |
| npm audit | CI 执行 `npm audit --omit=dev` | 生产依赖漏洞回潮会阻断 CI | 仅覆盖 npm advisory 范围 |
| 前端构建 | `npm run build` | 类型检查与 Vite 生产构建通过 | 不代表真实浏览器兼容矩阵 |
| BYOK 浏览器验证 | `npm run verify:byok`，cold-start 等待与失败诊断已加固 | OpenAI-compatible 设置流程可自动化验证 | mock API，不代表公网模型性能 |
| 暗色主题验证 | `npm run verify:dark`，cold-start 等待与失败截图已加固 | 主题切换与关键 canvas 渲染具备 sanity check | 不等同全量视觉回归 |
| UI guardrail | `npm --prefix frontend run verify:ui`（UI 静态扫描与 semantic sizing 红线） | UI 红线静态扫描与 semantic sizing guardrail 可重复执行 | 不等同全量视觉回归，不证明所有页面无样式缺陷 |

## 最近小重构带来的证据变化

| 变更域 | 代表提交 | 证据事实 | 论文写作边界 |
| --- | --- | --- | --- |
| 消息序号写入 | `f67abc1`、`e639d94`、`588bf73` | 系统消息统一走 message service；`nextSeqNum` 改为 latest max+1 | 可写降低稀疏序列重复风险，不可写并发绝对无冲突 |
| 报告任务幂等 | `5f0c786`、`31272bc`、`b39eee1` | `/finish` 与 worker 跳过/幂等路径有测试 | 可写幂等保护与回归测试，不可写生产级可靠投递 |
| BYOK 与 fallback | `588bf73`、`1cd55d2`、`57eba82` | openai-compatible 不静默 fallback；BYOK verify 覆盖设置流程 | 不可写所有模型故障零感知切换 |
| 语音链路 | `25ab32f`、`1cd55d2`、`b821bf7` | TTS executor、30s timeout、双 flag 与顺序测试 | 不可写真实 ASR/TTS 延迟基准 |
| 依赖治理 | `31272bc`、`588bf73`、`1cd55d2` | audit 清零并进入 CI | npm advisory 范围内的结论 |
| 架构/覆盖率门禁 | `b821bf7`、`6098ca0`、`9ab9465` | Sentrux 进入 CI；JaCoCo report-only；冗余 report goal 已消除 | 不得写成 coverage threshold gate |
| 自动化稳定性 | `57eba82`、`d23ec54`、`4b2e967` | BYOK 与 dark verify cold-start 等待、诊断、截图加固，最近 main CI 通过 | 证明脚本稳定性，不证明 UI 全量无缺陷 |
| UI semantic sizing / drift guardrail | `b114707`、`a23476a`、`975fbbe`、`1536947`、`e8fa5378` | semantic sizing token 引入、shadow guardrail 收紧、`verify:ui` 进入 main | 可写 UI 静态扫描与红线约束，不可写全量视觉回归 |
| 运行口径 | `51304d7`、`41b27b1`、`c974508` | Demo Twin 退役，收敛为 `start-dev` + `start-docker` | 旧 Demo 数据只能作为历史对照 |

## 仍需限制的论文表述

- 不得宣称已完成公网高并发压测。
- 不得宣称 RabbitMQ 已达到生产级可靠投递或消息零丢失。
- 不得宣称 ASR/TTS 在真实公网语音服务下完成低延迟性能基准。
- 不得宣称 BYOK fallback 可对 openai-compatible 自定义接口进行无感系统通道切换。
- 不得把 JaCoCo report-only 写成 coverage threshold gate。
- 不得把 Sentrux 当前有限规则写成完整架构正确性证明。

## 可进入第五章的稳妥写法

> 项目在 `origin/main` 当前同步基线下已形成自动化质量门禁：后端单元测试可生成 JaCoCo 覆盖率报告，前端构建、生产依赖审计、BYOK 设置流程验证、暗色主题验证与 UI guardrail（`verify:ui`，UI 静态扫描与 semantic sizing 红线）均纳入本地/CI 检查；同时通过 Sentrux 维护基础架构边界约束。`verify:ui` 只证明 UI 静态扫描与红线约束通过，不等同全量视觉回归。最近 `main` CI run `27815679764` 在 `4b2e967` 上通过，`npm audit --omit=dev` 当前返回 0 vulnerabilities。上述结果用于说明系统具备可重复的工程验证流程，但不等同于生产环境高并发性能测试或模型服务稳定性证明。
