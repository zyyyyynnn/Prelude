# 质量门禁证据快照（2026-07-13）

## 快照范围

- PR #19：`refactor: establish modular monolith architecture`
- PR #19 合并提交：`df41b5e21415160691eaf56927d3c5cd5e012c05`
- PR #20：`refactor: decouple interview application from api`
- 当前 `main` / PR #20 合并提交：`851fa5bf12c2f3737c30f13632b7e1759932eacd`
- 状态：证据已采集，待用户与审查官复核；不替代生产环境验证

## 验证结果

| 范围 | 验证 | 结果 |
| --- | --- | --- |
| PR #19 本地 | `sentrux check .` | 10 条规则通过，Quality 6872 |
| PR #19 本地 | `mvn clean test` | 219 个测试通过；JaCoCo 应用包门禁通过 |
| PR #19 本地 | 前端 build、`npm audit --omit=dev`、UI/BYOK/dark/a11y 检查 | 全部通过；audit 为 0 个漏洞 |
| PR #19 CI | runs `29237681005`、`29237710648` | 两个 `build` 均为 success |
| PR #20 本地 | `sentrux check .` | 10 条规则通过，Quality 6882 |
| PR #20 本地 | `mvn --file backend/pom.xml clean test` | 222 个测试通过；`jacoco:check` 通过 |
| PR #20 聚焦验证 | `ArchitectureBoundaryTest,InterviewControllerWebMvcTest` | 30 个测试通过；应用包覆盖率门禁通过 |
| PR #20 CI | run `29240597462` | `build` 为 success |

PR #20 CI 记录：<https://github.com/zyyyyynnn/Prelude/actions/runs/29240597462>

## 当前覆盖率门禁事实

`backend/pom.xml` 在 Maven `test` 阶段执行 JaCoCo `check`，对以下 application 包设置 instruction coverage 最低 `0.70`：

- `com.interview.interview.application`
- `com.interview.resume.application`
- `com.interview.insight.application`

因此，2026-06-19 快照中的“JaCoCo report-only”口径仅适用于当时基线，不再代表 2026-07-13 当前代码。

## CI 门禁范围

当前 CI 包含 whitespace diff check、Sentrux、后端构建与测试、JaCoCo 报告、前端依赖安装与 audit、前端 build、`verify:ui`、`verify:tokens`、`verify:byok`、`verify:dark`、`verify:a11y`。`capture:visual` 继续作为 artifact-only 证据采集，不作为像素差异阻断门禁。

## 证据限制

- 测试数量与 Sentrux 分数是对应提交上的阶段性快照，不是长期固定指标。
- 70% 门禁只覆盖上述三个 application 包的 instruction coverage，不代表全仓覆盖率达到 70%。
- `verify:a11y` 仅阻断 critical 级问题，不等同完整 WCAG 2 AA 合规。
- `verify:ui` 与 `verify:tokens` 是静态规则门禁，不等同全量视觉回归。
- `capture:visual` 是截图产物采集，不是像素 diff 阻断测试。
- 本快照不提供负载、长连接并发、消息零丢失或生产环境性能证据。
