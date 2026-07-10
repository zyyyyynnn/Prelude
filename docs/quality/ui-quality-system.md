# UI Quality System

`DESIGN.md` 是 UI 规范的唯一最高入口。本文件只描述当前自动化验证、Component Lab、视觉回归、可访问性、token schema 与 Component Lab 的维护方式，不复述 `DESIGN.md` 的样式与组件约束。

## 1. Scope

- 静态 guardrail：`verify:ui`，覆盖 transition-all / window.confirm / 原生 title= / shadow-md / shadow-lg / border-border / h-[30-34px] / Tailwind arbitrary px / 业务组件裸 px / magic height ratio / spacing calc，以及业务组件 `:focus-visible` 的共享 shadow token 约束。
- Token schema：`verify:tokens`，校验 `frontend/tokens/ui-tokens.json` schema 完整性、`--shadow-*` 原始值仅在 token 定义块、`--z-index-*` 唯一性、design-locked 值（260 / 51 / 800 / 960 / 500 / 34 / 30）。
- 可访问性：`verify:a11y`，Playwright + axe-core 9 个场景，仅 fail **critical** axe violations；serious 作为 backlog。
- 视觉回归：`capture:visual` / `verify:visual`，22 个 scenario 抓图为 PNG artifact；当前 **artifact-only + continue-on-error**，不作为 blocking diff gate。
- Component Lab：dev-only `/components-lab` 路由，`import.meta.env.DEV` 条件注册，生产构建被 Vite tree-shake 掉。

## 2. Commands

```powershell
# 静态 guardrail
npm --prefix frontend run verify:ui

# token schema
npm --prefix frontend run verify:tokens

# 可访问性
npm --prefix frontend run verify:a11y

# 视觉回归（artifact-only）
npm --prefix frontend run capture:visual
```

所有脚本使用 Node 内置实现，不引入 Style Dictionary、Storybook 等额外依赖。

## 3. CI status

| Gate | 范围 | 类型 |
| --- | --- | --- |
| `verify:ui` | 静态 guardrail（含 component focus shadow token） | blocking |
| `verify:tokens` | token schema | blocking |
| `verify:a11y` | axe-core critical only | blocking |
| `verify:byok` | BYOK 设置流程 mock API 验证 | blocking |
| `verify:dark` | 暗色主题 sanity check | blocking |
| `capture:visual` | 22 scenario 抓图 | artifact-only (`continue-on-error: true`) |

CI 浏览器策略：复用系统 Microsoft Edge channel，不再下载 Playwright Chromium。

- `jobs.build.env` 注入 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`。
- `frontend/tests/_helpers/playwright-base.ts` 在 `process.env.CI` 为真时给 `baseUse` 注入 `channel: 'msedge'`。
- `npx playwright install` 步骤被整体移除，消除 windows-latest 上 Chromium + headless-shell CDN 下载 stall。

本地 dev 不受影响：`process.env.CI` 未设置时保留默认 Playwright Chromium；`frontend/playwright.local.config.ts` 显式 `channel: 'msedge'`，行为一致。

## 4. Visual coverage

22 scenarios 抓图输出至 `frontend/tests/visual/__screenshots__/`（已 gitignore）：

| # | 场景 |
| --- | --- |
| 1 | login page (light) |
| 2 | login page (dark) |
| 3 | sidebar expanded |
| 4 | sidebar collapsed |
| 5 | interview empty state |
| 6 | composer text-mode |
| 7 | composer voice-mode（fallback） |
| 8 | settings modal — profile tab |
| 9 | settings modal — theme tab |
| 10 | settings modal — LLM tab |
| 11 | position dropdown open |
| 12 | resume dropdown open |
| 13 | interview generating state（fallback） |
| 14 | report paper state（fallback） |
| 15 | analytics dashboard |
| 16 | components lab (light) |
| 17 | components lab (dark) |
| 18 | interview messages hide live score/hint（assertion only） |
| 19 | structured report carousel |
| 20 | old Markdown report fallback（assertion only） |
| 21 | structured report mobile 390px |
| 22 | structured report PDF non-empty download（assertion only） |

voice-mode / generating 当前为 fallback capture（依赖 active session）。结构化报告、旧 Markdown fallback、小屏和 PDF 已有独立场景。

## 5. A11y coverage

9 scenarios，仅 fail **critical** axe violations：

| # | 场景 | 类型 |
| --- | --- | --- |
| 1 | login page — no critical axe violations | axe-core wcag2a/aa/21a/21aa |
| 2 | workspace shell — no critical axe violations | axe-core |
| 3 | settings modal — opens, focus inside, Esc closes | keyboard path + axe |
| 4 | position dropdown — click + Esc | keyboard path |
| 5 | settings LLM tab — Combobox ArrowDown + Esc | keyboard path + axe |
| 6 | sidebar collapse button — Enter | keyboard path |
| 7 | composer textarea — Ctrl+Enter / Meta+Enter | keyboard path |
| 8 | no native `title=` attribute | guardrail integration |
| 9 | structured report carousel controls and semantic review list | keyboard path + axe-core |

"`verify:a11y` PASS" ≠ "无 a11y 问题"。Serious violations（主要是 color-contrast）记录在 backlog，由 UI token 团队协调 brand-tone 后再升级为严格模式。

## 6. Component Lab

`/components-lab` 是 dev-only 路由：

```ts
...(import.meta.env.DEV
  ? [
      {
        path: '/components-lab',
        name: 'components-lab',
        component: () => import('../views/ComponentLabView.vue'),
        meta: { public: true, devOnly: true },
      },
    ]
  : []),
```

- `import.meta.env.DEV === false` 时 Vite/Rolldown tree-shake 路由注册 + lazy import 块。
- `meta.public: true` 让 dev 环境免鉴权访问。
- 不在用户侧 sidebar / help / auth redirect 中暴露。

覆盖组件族：Button / Input / Textarea / Select / DropdownMenu / Combobox / Dialog / Tooltip / Badge / Card / EmptyState / SegmentedControl / Workspace excerpt / Composer excerpt / Message bubble。

## 7. Token schema

`frontend/tokens/ui-tokens.json` 是只读 index，不生成 CSS。分类与 design-locked 值：

| 字段 | 值 |
| --- | --- |
| `ui-height-base` | `34px` |
| `ui-height-compact` | `30px` |
| `layout-sidebar-inline-size` | `260px` |
| `layout-sidebar-collapsed-inline-size` | `51px` |
| `layout-workspace-content-max-inline-size` | `800px` |
| `layout-settings-dialog-max-inline-size` | `960px` |
| `layout-settings-dialog-min-block-size` | `500px` |

校验规则：

| Rule key | 行为 |
| --- | --- |
| `raw-only-in-token-definitions` | shadow 原始值必须仅出现在 `:root` / `:root.dark` / `.dark` / `@theme` 中 |
| component focus shadow | 业务组件 scoped CSS 的 `:focus-visible` 使用 `box-shadow` 时必须引用 `--shadow-icon-action-focus` |
| `values-must-be-unique` | `--z-index-*` token 数值必须唯一 |
| `design-locked` | 列出的 token 数值必须与 schema `design_lock_values` 块一致 |

## 8. Backlog

| ID | 风险 | 状态 |
| --- | --- | --- |
| R1 | voice-mode / generating 视觉场景依赖 active session，当前使用 workspace-empty fallback capture | 跟踪 |
| R2 | a11y serious `color-contrast` 命中 5+ 处（login / workspace / LLM tab / settings closed） | UI token 团队协调 brand-tone |
| R3 | Component Lab 仅 desktop viewport，响应式 split view 缺位 | 跟踪 |
| R4 | token migration 到 Style Dictionary（生成式 CSS）作为未来路径 | 本轮不做 |
| R5 | `capture:visual` 当前 artifact-only，是否升级 blocking 待 1-2 个 release 周期回归 main 验证后再决定 | 跟踪 |
| R6 | token `legacy-utility` 类别（9 个 token）整合到统一 namespace | 后续 |

## 9. 过程记录

`thesis-assets/evidence/phase-reports/ui-phase2-quality-system-2026-06.md` 保留 Phase 0–6 的 commit 列表、阶段完成情况、修改文件清单与最终验证结果。本文件不再展开过程叙述。

## 10. 相关文档

- `DESIGN.md`：UI 规范最高入口
- `docs/quality/local-review-checklist.md`：本地预检命令
- `docs/quality/risk-register.md`：工程风险台账
- `docs/runtime-modes.md`：运行入口
- `docs/setup.md`：本地环境配置
