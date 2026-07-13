# 阶段报告索引

## 当前 active 报告

| 文件 | 用途 | 当前定位 |
| --- | --- | --- |
| `phase-2.13-modular-monolith-sync-2026-07-13.md` | 两轮模块化重构的项目漂移、证据与正文影响评估 | 最新漂移入口 / 待用户与审查官复核 |
| `phase-3-readiness-freeze-2026-06-20.md` | 阶段 3 Final Evidence Freeze 与答辩准备核对 | 上次冻结入口 / 历史口径（已被 2.13 部分取代，质量门禁入口以 07-13 为准） |

## 历史归档

`archive/` 子目录保留阶段 2.10 至 2.12、2.11 系列、pre-rewrite-final-gate、ui-phase2-quality-system 等历史过程报告。

历史归档文件仅作阶段演进对照，**不作为当前事实入口**。当前事实入口以 `meta/final-evidence-lock.md` 为准。其中：

- 凡引用已删除 `InterviewServiceImpl` 的历史片段，正文不得直接引用，须先与当前源码核对或换新证据。
- 凡写 JaCoCo report-only 的历史口径，已被 2026-07-13 的三个 application 包 70% 阻断门禁取代。
- 凡写 Demo Twin / start-demo / start-real / 8081/5174 的历史入口，已被 `start-dev` + `start-docker` 取代。

| 归档文件 | 归档原因 |
| --- | --- |
| `phase-2.10-evidence-readiness.md` | 阶段 2.10 历史过程，已被后续 lock + matrix 收口 |
| `phase-2.11A-test-evidence-review.md` | 阶段 2.11A 历史过程，引用已删 InterviewServiceImpl |
| `phase-2.11B-figure-plan-review.md` | 阶段 2.11B 历史过程，引用已不存在 figure-assets-plan.md |
| `phase-2.11C-diagram-refresh-report.md` | 阶段 2.11C 历史过程，引用已删 InterviewServiceImpl |
| `phase-2.11C-fix-diagram-readability-review.md` | 阶段 2.11C-Fix 可读性重构历史记录 |
| `phase-2.12-project-drift-sync-2026-06-19.md` | 阶段 2.12 漂移同步历史，含 JaCoCo report-only 旧口径 |
| `pre-rewrite-final-gate-2026-06-14.md` | 正文改写前闸门历史，引用已退役 start-demo 入口 |
| `ui-phase2-quality-system-2026-06.md` | UI Phase 2 体系建设过程记录，当前 UI 质量门禁以 quality-gates-2026-07-13.md 为准 |
