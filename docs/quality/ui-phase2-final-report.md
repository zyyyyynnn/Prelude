# UI Phase 2 — 最终质量体系报告

> Phase 6 在 P1-P5 完成后做最终全量审查 + 报告。本文档是整个 Phase 2 UI 体系建设工作的汇总基线。

## 1. 分支名
`codex/ui-quality-system-phase2`（基于 `main`，HEAD `e8fa537`）

## 2. Commit 列表（按顺序）

| # | SHA | Subject |
| --- | --- | --- |
| Phase 0 | `ea8f8bf` | docs(ui): record phase 2 ui quality baseline |
| Phase 1 | `ad7ff0a` | test(ui): add visual regression coverage |
| Phase 2 | `19d931c` | test(ui): add accessibility verification |
| Phase 3 | `96fa9d8` | feat(ui): add development component lab |
| Phase 4 | `a87e35c` | test(ui): add token schema verification |
| Phase 5 | `d3f0d59` | ci(ui): enforce ui quality gates |
| Cleanup | `55efdcb` | chore(repo): untrack transient output bundles and pin CRLF in gitignore |

## 3. 阶段完成情况

| Phase | 主题 | 状态 | 报告文件 |
| --- | --- | --- | --- |
| 0 | 二次全面 UI 审查与基线报告 | ✅ | `ui-phase2-baseline.md` |
| 1 | 视觉回归体系 | ✅ | `ui-visual-regression.md` |
| 2 | a11y 系统化 | ✅ | `ui-a11y.md` |
| 3 | Component Lab | ✅ | `ui-component-lab.md` |
| 4 | token schema 化 | ✅ | `ui-token-schema.md` |
| 5 | CI 接入 | ✅ | `.github/workflows/ci.yml`（diff in commit `d3f0d59`） |
| 6 | 最终二次全面审查 | ✅ | 本文件 |

## 4. 新增文件列表

| 文件 | 用途 |
| --- | --- |
| `docs/quality/ui-phase2-baseline.md` | Phase 0 基线报告 + P1-P5 计划 |
| `docs/quality/ui-visual-regression.md` | Phase 1 视觉回归设计 + 操作手册 |
| `docs/quality/ui-a11y.md` | Phase 2 a11y 严重度策略 + 已知 backlog |
| `docs/quality/ui-component-lab.md` | Phase 3 Component Lab dev-only 契约 + 覆盖矩阵 |
| `docs/quality/ui-token-schema.md` | Phase 4 token schema 分类 + 迁移路径 |
| `frontend/playwright.visual.config.ts` | 视觉回归 Playwright config |
| `frontend/playwright.a11y.config.ts` | a11y Playwright config |
| `frontend/tests/visual/ui-visual.spec.ts` | 17-scenario 视觉回归 spec |
| `frontend/tests/visual/README.md` | 视觉回归操作手册 |
| `frontend/tests/a11y/ui-a11y.spec.ts` | 8-scenario a11y spec |
| `frontend/src/views/ComponentLabView.vue` | Component Lab 主视图 |
| `frontend/src/components/lab/ComponentLabSection.vue` | Component Lab section 容器 |
| `frontend/tokens/ui-tokens.json` | 157 tokens / 13 categories 的 schema |
| `frontend/scripts/verify-ui-tokens.cjs` | Node 内置 token schema 校验脚本 |

## 5. 修改文件列表

| 文件 | 变更 |
| --- | --- |
| `frontend/package.json` | 新增 4 个 npm script：capture:visual / verify:visual / verify:a11y / verify:tokens |
| `frontend/src/router/index.ts` | 新增 dev-only `/components-lab` 路由（`import.meta.env.DEV` 条件注册） |
| `frontend/src/components/workspace/AppSidebar.vue` | Phase 2 加 aria-label 到 session list button（修复 axe button-name critical violation） |
| `frontend/src/components/workspace/LlmSettingsPanel.vue` | Phase 2 给 4 个 SelectTrigger 加 aria-label（修复 axe button-name critical violation） |
| `frontend/.gitignore` | 排除 `tests/visual/__screenshots__/` + `output/screenshots/visual/` |
| `.gitignore` | 排除 `output/screenshots/dev/`（本地 dev 截图临时目录） |
| `.github/workflows/ci.yml` | Phase 5 新增 5 个 CI step + 2 个 artifact 上传 |
| `docs/quality/local-review-checklist.md` | 必备命令 + CI 接入说明扩充 |

## 6. 新增 npm scripts

| Script | 命令 | 阶段 | 类型 |
| --- | --- | --- | --- |
| `capture:visual` | `playwright test -c playwright.visual.config.ts` | P1 | artifact-only（CI 中 `continue-on-error: true`） |
| `verify:visual` | `playwright test -c playwright.visual.config.ts` | P1 | 同上（预留为 blocking 入口） |
| `verify:a11y` | `playwright test -c playwright.a11y.config.ts` | P2 | blocking |
| `verify:tokens` | `node scripts/verify-ui-tokens.cjs` | P4 | blocking |

## 7. 新增 devDependencies 及理由

| 包 | 版本 | 阶段 | 理由 |
| --- | --- | --- | --- |
| `@axe-core/playwright` | 4.11.3 | P2 | axe-core WCAG 2 AA 自动化扫描。是任务硬性原则唯一允许新增的 devDependency。 |
| `@playwright/test` | (existing) | P1, P2, P3 | 已在 `frontend/package.json` devDependencies 中，1.59.1；Phase 1/2/3 全部复用。 |

**Phase 3 不引入 Storybook**（任务硬性原则）；通过自建 `ComponentLabView` 实现。

**Phase 4 不引入 Style Dictionary**（任务硬性原则）；schema 是只读 index，不是 CSS 生成器。

## 8. 视觉覆盖场景（Phase 1）

17 个 scenario 全部 PASS，PNG artifact 输出至 `frontend/tests/visual/__screenshots__/`（已 gitignore）：

| # | 场景 |
| --- | --- |
| 1 | login page (light) |
| 2 | login page (dark) |
| 3 | sidebar expanded |
| 4 | sidebar collapsed |
| 5 | interview empty state |
| 6 | composer text-mode |
| 7 | composer voice-mode (placeholder) |
| 8 | settings modal — profile tab |
| 9 | settings modal — theme tab |
| 10 | settings modal — LLM tab |
| 11 | position dropdown open |
| 12 | resume dropdown open |
| 13 | interview generating state (fallback capture, see `ui-visual-regression.md`) |
| 14 | report paper state (fallback capture) |
| 15 | analytics dashboard |
| 16 | components lab (light) |
| 17 | components lab (dark) |

## 9. a11y 覆盖场景（Phase 2）

8 个 scenario 全部 PASS（**critical only** gate）：

| # | 场景 | 类型 |
| --- | --- | --- |
| 1 | login page — no **critical** axe violations | axe-core wcag2a/aa/21a/21aa |
| 2 | workspace shell — no **critical** axe violations | axe-core |
| 3 | settings modal — opens, focus inside, Esc closes | keyboard path + axe |
| 4 | position dropdown — click + Esc | keyboard path |
| 5 | settings LLM tab — Combobox ArrowDown + Esc | keyboard path + axe |
| 6 | sidebar collapse button — Enter | keyboard path |
| 7 | composer textarea — Ctrl+Enter / Meta+Enter | keyboard path |
| 8 | no native `title=` attribute | guardrail integration |

> **口径说明**：`verify:a11y` 只 fail **critical** axe violations；serious violations（主要是 color-contrast 5+ 处命中）记录到 console + docs/quality/ui-a11y.md backlog 作为 P2 项，由 UI token 团队 + DESIGN.md 协调 brand-tone 后再升级为严格模式。"verify:a11y PASS" ≠ "a11y 无严重问题"。详见 `ui-a11y.md` Severity policy 段落。

## 10. Component Lab 覆盖（Phase 3）

11 类组件 × 关键状态：

| 组件 | 状态覆盖 |
| --- | --- |
| Button | 6 variants × 3 sizes + loading + disabled |
| Input | default / disabled / error-style hint |
| Select / DropdownMenu | closed / open · long label |
| Dialog | settings-like modal |
| Tooltip | hover/focus trigger · long text |
| Badge | default / destructive / secondary / outline |
| Card / EmptyState | container + empty |
| SegmentedControl | 1 / 2 / 3 / long-label items |
| Workspace excerpt | expanded / collapsed |
| Composer excerpt | empty / text / voice |
| Message bubble | user / assistant / judge |

## 11. Token Schema 覆盖（Phase 4）

13 个 categories，157 tokens，218 declarations：

| Category | Token 数 | 备注 |
| --- | --- | --- |
| color | 21 | 品牌 + 语义颜色 |
| spacing | 13 | 4/8/16 阶梯 + 异常值 |
| radius | 7 | 圆角阶梯 |
| shadow | 13 | 仅在 token 定义块 |
| motion | 10 | 时长 / 缓动 / 过渡 |
| font | 10 | 字体族 + 字号 |
| ui-height | 4 | base 34 / compact 30 locked |
| layout | 11 | Sidebar 260/51 / dialog 960/500 / workspace 800 |
| content | 3 | message / judge / judge-hint |
| z-index | 3 | values must be unique |
| header-composer | 2 | header-height / composer-height |
| chart-brand | 14 | chart-* / brand-* / rose-* |
| shadcn-tailwind-theme | 29 | Tailwind v4 默认 theme |
| legacy-utility | 9 | Phase 4 之后整合 |
| scoped-component-private | 0 | 不索引，由 verify:ui guardrail 管理 |

**Design-locked values（自动校验）**：

```json
{
  "ui-height-base": "34px",
  "ui-height-compact": "30px",
  "layout-sidebar-inline-size": "260px",
  "layout-sidebar-collapsed-inline-size": "51px",
  "layout-workspace-content-max-inline-size": "800px",
  "layout-settings-dialog-max-inline-size": "960px",
  "layout-settings-dialog-min-block-size": "500px"
}
```

## 12. CI 门禁列表（Phase 5）

### Blocking

| Step | 命令 |
| --- | --- |
| Check whitespace | `git diff --check` |
| Check architecture rules | `sentrux check .` |
| Build backend | `mvn -q -DskipTests compile` |
| Check backend tests | `mvn -q test` |
| Audit frontend production dependencies | `npm audit --omit=dev` |
| Build frontend | `npm run build` |
| **Verify UI guardrails** | `npm run verify:ui` |
| **Verify token schema** | `npm run verify:tokens` |
| Verify BYOK settings flow | `npm run verify:byok` |
| Verify dark theme flow | `npm run verify:dark` |
| **Verify UI accessibility** | `npm run verify:a11y` |

### Artifact-only + non-blocking

| Step | 命令 |
| --- | --- |
| Install Playwright chromium browser | `npx --no-install playwright install --with-deps chromium` |
| **Capture UI visual baseline** | `npm run capture:visual`（`continue-on-error: true`） |

### Artifacts

| Artifact | 路径 | 保留 |
| --- | --- | --- |
| `backend-jacoco-report` | `backend/target/site/jacoco` | 14 天 |
| `ui-visual-baseline` | `frontend/tests/visual/__screenshots__/`, `output/screenshots/visual/` | 14 天 |
| `playwright-test-artifacts` | `frontend/test-results/` | 7 天 |

## 13. 验证命令结果（Phase 6 final run）

| 命令 | 结果 |
| --- | --- |
| `git status --short` | ✅ 工作树干净（除 Phase 1/3 新文件视情况） |
| `git diff --check` | ✅ exit=0 |
| `npm --prefix frontend run build` | ✅ built in 1.65s |
| `npm --prefix frontend run verify:ui` | ✅ PASS |
| `npm --prefix frontend run verify:tokens` | ✅ PASS（0 violations / 0 notes / 157 schema / 218 declarations） |
| `npm --prefix frontend run verify:byok`（cold Vite） | ✅ EXIT=0 |
| `npm --prefix frontend run verify:dark` | ✅ EXIT=0 |
| `npm --prefix frontend run verify:a11y` | ✅ 8 / 8 通过 |
| `npm --prefix frontend run capture:visual` | ✅ 17 / 17 通过 |
| `mvn -f backend/pom.xml test` | ✅ 121 tests, 0 failures |
| `npx --yes js-yaml .github/workflows/ci.yml` | ✅ YAML 语法 OK |

## 14. 静态扫描结果（Phase 6 final run）

| # | 扫描 | 业务组件命中 | token 文件命中 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | `transition-all / window.confirm / title=` | 0 | 0 | ✅ |
| 2 | `shadow-md / shadow-lg / border-border / h-[30/32/34px]` | 0 | 0 | ✅ |
| 3 | `rgba / dark:bg- / bg-white / text-white / bg-black / text-black / 十六进制` | 0 | 47（`styles/index.css` 内 token 定义允许） | ✅ |
| 4 | Tailwind arbitrary px class | 0 | 0 | ✅ |
| 5 | 属性侧 `calc(var(--ui-height-*) * 数字)` | 0 | 5（component scoped 变量声明） | ✅ |
| 6 | 简单半阶 / 负向 `calc(var(--spacing-...)` | 1（`padding-block: calc(var(--spacing-2xl) + var(--spacing-lg))` 组件几何，已命名） | — | ✅ |
| 7 | `box-shadow:` raw (不含 `var()`) | 0 | 2（`box-shadow: none !important` 视为 opt-out） | ✅ |
| 8 | `outline / border-radius / transform: translate(Npx)` | 0 | token 定义内 6 处允许 | ✅ |
| 9 | `\bz-\d+\b` Tailwind z-N | 0 | — | ✅ |
| 10 | `z-index: <num>` 属性侧 | 0 | 4（`styles/index.css` token 定义允许） | ✅ |

业务组件新增命中：**0**。

## 15. 残留问题

### P0（必须为 0） ✅
无。

### P1（必须为 0） ✅
无。

### P2（可记录但不阻塞）

| 风险 | 描述 | 影响 | 后续跟踪 |
| --- | --- | --- | --- |
| R2 a11y backlog | axe serious `color-contrast` 命中 5+ 处（login / workspace / LLM tab / settings closed）。 | WCAG 2 AA 边缘未完全达标 | UI token 团队协调 brand-tone 颜色调整；可能改 DESIGN.md |
| R6 visual 稳定性 | capture:visual 在 CI 尚未跑过，font / DPR flake 风险未验证。 | 当前 artifact-only + continue-on-error | 1-2 个 release 周期后回归 main 验证 → 升级为 blocking |

### P3（backlog，可后续）

| 风险 | 描述 |
| --- | --- |
| R1 视觉回归缺口 | voice-mode / generating / report-paper 三个 scenario 需要 active session 才能 capture 完整状态；目前使用 workspace-empty fallback capture。推动方案：通过 dev fixture 创建真实 session 后 capture，再在 Phase 5+ 升级。 |
| R3 组件状态矩阵 | Component Lab 仅 desktop viewport；响应式 split view 是 follow-up。 |
| R4 token schema | Migration 到 Style Dictionary（生成式 CSS）作为未来路径，本轮不做。 |
| R5 CI 门禁 | visual 当前 artifact-only，迁移路径记录在 `ui-visual-regression.md`。 |
| token legacy-utility | schema 中 `legacy-utility` 类别（9 个 token）待后续整合到统一 namespace。 |

## 16. 停止条件检查

| 条件 | 触发？ | 说明 |
| --- | --- | --- |
| 需要改后端 API 才能继续 | 否 | 所有 UI 验证用 mock route |
| 需要重设 DESIGN.md 既定 UI 规范 | 否 | 260 / 51 / 800 / 960 / 500 / 34 / 30 全部原值保留 |
| 视觉回归需要大量业务 mock | 部分 | 3 个场景用 fallback（见 R1）；其余 14 个场景用 mock route |
| a11y 修复会改变核心交互 | 否 | 仅添加 aria-label，不改交互 |
| token schema 会迫使大规模重写 `index.css` | 否 | schema 是只读 index，未触发任何 CSS 重写 |
| CI 运行时间明显失控 | 否 | 估算总时长 +3-5min（Playwright install + capture） |
| 新依赖超过 `@axe-core/playwright` 或 Playwright 必要依赖 | 否 | 仅 @axe-core/playwright；其余全部复用现有 devDependencies |
| 任一阶段出现 P0/P1，但无法在本阶段最小修复 | 否 | 无 P0/P1 |

**无停止条件触发**。

## 17. 最终结论

- **是否建议合并到 main**：✅ **是**
- **是否存在 P0/P1**：✅ **否**
- **是否需要继续修复**：✅ **否**（P2/P3 为 backlog 跟踪项，不阻塞合并）
- **合并后是否需要删除分支**：✅ **是**（`codex/ui-quality-system-phase2`）

合并后建议在 main 上保留所有 Phase 2 commit，让 CI 第一次跑完整流程，再根据日志调整 strict 度（如 visual 何时升级为 blocking）。

## 18. PR 链接

https://github.com/zyyyyynnn/Prelude/pull/new/codex/ui-quality-system-phase2
