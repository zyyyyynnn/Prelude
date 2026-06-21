# 质量门禁与自动化验证证据快照（2026-06-19）

## 文件定位

本文件是论文第五章“构建与自动化验证记录”的补充证据。

- 对应位置：第五章构建与自动化验证补充证据；当前正文未单列“构建与自动化验证记录表”。
- 证据性质：工程质量门禁与自动化验证链路记录。
- 适用边界：证明当前项目具备可重复的 CI 门禁与本地预检分层验证基础，不证明生产环境高并发性能、模型质量稳定性或消息队列零丢失。

## 当前基线

| 项 | 内容 |
| --- | --- |
| 当前同步基线（`origin/main` HEAD） | `ffb617a6efdcd88975c9985020eb81776a984375` |
| 代码行为事实基线（最近 CI 通过） | `ffb617a`（CI run `27878325552` 在该基线上通过；21 steps success） |
| 文档收口基线 | `4b2e967` 仅作为阶段 3 原始 freeze 审查基线保留，**不是**当前最近 CI 通过事实；当前 active evidence 入口同步基线为 `ffb617a`（见 `final-evidence-lock.md`） |
| 最近治理范围 | 约最近 30 次提交，覆盖 UI token 收敛、UI semantic sizing 与 drift guardrail、verify:ui / verify:tokens / verify:a11y / capture:visual 自动化门禁、dev fixture 收敛、语音链路硬化、消息序号一致性、报告任务幂等、BYOK 验证、Sentrux/JaCoCo/npm audit 门禁、CI whitespace diff merge-base 口径修复，以及阶段 2 历史文档降噪收口 |
| 运行入口口径 | `start-dev` + `start-docker`；旧 Demo Twin / start-real / start-demo 均为历史状态 |
| npm audit | `npm --prefix frontend audit --omit=dev` 返回 `found 0 vulnerabilities` |
| BYOK / dark verify | 已进入 CI（blocking），并完成 cold-start 等待与失败诊断加固 |
| UI guardrail | `verify:ui`（UI 静态扫描与 semantic sizing 红线）已接入 CI 作为 blocking gate；本地预检可重复执行 |
| Token schema | `verify:tokens` 已接入 CI 作为 blocking gate |
| A11y | `verify:a11y`（axe-core + keyboard paths）已接入 CI 作为 blocking gate，仅 fail critical axe violations |
| Visual capture | `capture:visual` 已接入 CI 作为 artifact-only gate（`continue-on-error: true`），不作为像素 diff gate |
| Playwright | CI 复用系统 Microsoft Edge channel；`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 跳过 Playwright Chromium 下载；本地 dev 不受影响 |
| whitespace diff | PR 路径使用 `git merge-base base.sha HEAD` 取得 diff 起点，避开 PowerShell 三引号 range operator；push 路径仍用 `HEAD~1` |
| 论文状态 | 阶段 3 执行准备仅冻结 evidence 与答辩材料口径，不修改 `chapters/*.md`；正文修订仍需用户和审查官确认 |

## CI 门禁与本地预检质量门禁

| 门禁项 | 范围 | 当前实现 | 可支撑结论 | 限制 |
| --- | --- | --- | --- | --- |
| whitespace diff check | CI | push 使用 `HEAD~1`；PR 使用 `git merge-base $baseSha HEAD` 后 `git diff --check $mergeBase HEAD` | 可阻断新增 whitespace 错误 | 不等同全量格式化 |
| Sentrux 架构规则 | CI | CI 下载固定版本并校验 SHA256，执行 `sentrux check .` | 架构边界规则进入 CI | 当前规则数量有限 |
| 后端测试 | CI | `mvn -q test`；JaCoCo report 绑定 test phase | 单元测试通过并生成覆盖率报告 | coverage report-only，无阈值 |
| JaCoCo artifact | CI | CI 上传 `backend-jacoco-report` | 可供审查覆盖率明细 | 不阻塞构建 |
| npm audit | CI | CI 执行 `npm audit --omit=dev` | 生产依赖漏洞回潮会阻断 CI | 仅覆盖 npm advisory 范围 |
| 前端构建 | CI | `npm run build` | 类型检查与 Vite 生产构建通过 | 不代表真实浏览器兼容矩阵 |
| BYOK 浏览器验证 | CI | `npm run verify:byok`，cold-start 等待与失败诊断已加固 | OpenAI-compatible 设置流程可自动化验证 | mock API，不代表公网模型性能 |
| 暗色主题验证 | CI | `npm run verify:dark`，cold-start 等待与失败截图已加固 | 主题切换与关键 canvas 渲染具备 sanity check | 不等同全量视觉回归 |
| UI guardrail | CI + 本地预检 | `npm run verify:ui`（UI 静态扫描与 semantic sizing 红线） | UI 红线静态扫描与 semantic sizing guardrail 可重复执行，CI 阻断新增违规 | CI 只阻断新增违规，不证明全部历史页面无样式缺陷；不等同全量视觉回归 |
| Token schema | CI + 本地预检 | `npm run verify:tokens`（schema 完整性、shadow 原始值位置、z-index 唯一性、design-locked 值） | token schema 与 design-locked 值可在 CI 校验 | 不生成 CSS，不替代 DESIGN.md 的人工审查 |
| A11y | CI + 本地预检 | `npm run verify:a11y`（axe-core + keyboard paths，8 个 scenario） | 仅 fail critical axe violations，可重复执行 | serious violations（color-contrast 等）记入 backlog，不代表完整 WCAG 2 AA |
| Visual capture | CI（artifact-only） | `npm run capture:visual`（17 个 scenario，Microsoft Edge），`continue-on-error: true` | 提供 17 个场景的 PNG artifact，可人工 review | 不做像素 diff，不作为 blocking gate；voice-mode / generating / report-paper 当前为 fallback capture |

## 最近小重构带来的证据变化

| 变更域 | 代表提交 | 证据事实 | 论文写作边界 |
| --- | --- | --- | --- |
| 消息序号写入 | `f67abc1`、`e639d94`、`588bf73` | 系统消息统一走 message service；`nextSeqNum` 改为 latest max+1 | 可写降低稀疏序列重复风险，不可写并发绝对无冲突 |
| 报告任务幂等 | `5f0c786`、`31272bc`、`b39eee1` | `/finish` 与 worker 跳过/幂等路径有测试 | 可写幂等保护与回归测试，不可写生产级可靠投递 |
| BYOK 与 fallback | `588bf73`、`1cd55d2`、`57eba82` | openai-compatible 不静默 fallback；BYOK verify 覆盖设置流程 | 不可写所有模型故障零感知切换 |
| 语音链路 | `25ab32f`、`1cd55d2`、`b821bf7` | TTS executor、30s timeout、双 flag 与顺序测试 | 不可写真实 ASR/TTS 延迟基准 |
| 依赖治理 | `31272bc`、`588bf73`、`1cd55d2` | audit 清零并进入 CI | npm advisory 范围内的结论 |
| 架构/覆盖率门禁 | `b821bf7`、`6098ca0`、`9ab9465` | Sentrux 进入 CI；JaCoCo report-only；冗余 report goal 已消除 | 不得写成 coverage threshold gate |
| 自动化稳定性 | `57eba82`、`d23ec54`、`4b2e967` | BYOK 与 dark verify cold-start 等待、诊断、截图加固，最近 main CI 通过 | 证明脚本稳定性 |
| UI semantic sizing / drift guardrail | `b114707`、`a23476a`、`975fbbe`、`1536947` | semantic sizing token 引入、shadow guardrail 收紧 | 可写 UI 静态扫描与红线约束，不可写全量视觉回归 |
| UI 自动化门禁（verify:ui / tokens / a11y / capture:visual） | `a87e35c`、`19d931c`、`96fa9d8`、`d3f0d59`、`b579625` | `verify:tokens` / `verify:a11y` / `verify:ui` 在 CI 作为 blocking gate；`capture:visual` 作为 artifact-only；Playwright 改用系统 Microsoft Edge | 可写静态扫描、token schema、critical-only a11y、artifact-only capture；不可写全量视觉回归或完整 WCAG 达标 |
| whitespace PR diff merge-base | `ffb617a` | PR 路径用 `git merge-base $baseSha HEAD` 替换三引号形式，避开 PowerShell range operator | 修正后行为可证明；旧形式不应再被引用 |
| 运行口径 | `51304d7`、`41b27b1`、`c974508` | Demo Twin 退役，收敛为 `start-dev` + `start-docker` | 旧 Demo 数据只能作为历史对照 |

## 仍需限制的论文表述

- 不得宣称已完成公网高并发压测。
- 不得宣称 RabbitMQ 已达到生产级可靠投递或消息零丢失。
- 不得宣称 ASR/TTS 在真实公网语音服务下完成低延迟性能基准。
- 不得宣称 BYOK fallback 可对 openai-compatible 自定义接口进行无感系统通道切换。
- 不得把 JaCoCo report-only 写成 coverage threshold gate。
- 不得把 Sentrux 当前有限规则写成完整架构正确性证明。
- 不得把 `verify:a11y` critical-only gate 写成完整 WCAG 2 AA 达标。
- 不得把 `capture:visual` artifact-only 写成像素 diff blocking gate 或视觉回归全覆盖。
- 不得把 `verify:ui` 静态红线扫描写成全量视觉回归或 UI 完全无缺陷。

## 可进入第五章的稳妥写法

> 项目在 `origin/main` 当前同步基线下已形成分层自动化质量门禁：CI（blocking）包含 whitespace diff check、Sentrux、后端测试、JaCoCo report artifact、npm audit、前端 build、`verify:ui` 静态红线扫描、`verify:tokens` token schema、`verify:a11y` 仅 critical axe violations、`verify:byok`、`verify:dark`；`capture:visual` 作为 artifact-only（`continue-on-error: true`）上传 17 个场景的 PNG 供人工 review。Playwright 在 CI 复用系统 Microsoft Edge channel（`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`），不下载 Chromium。CI 并不证明生产环境高并发性能、模型质量稳定性或消息队列零丢失。当前 `origin/main` 在 `ffb617a` 基线上的 CI run `27878325552`（21 steps success）已通过；`npm audit --omit=dev` 当前返回 0 vulnerabilities。`4b2e967` 与 `27815679764` 仅作为阶段 3 原始 freeze 审查历史保留，run id 不作为正文事实引用。
