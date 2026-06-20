# UI Accessibility (a11y) Verification

> Phase 2 of the Phase 2 UI quality system. See also `ui-phase2-baseline.md` and `ui-visual-regression.md`.

## What this phase produces

A Playwright + axe-core accessibility harness that validates the keyboard paths and ARIA semantics that the static guardrail layer cannot:

- `frontend/playwright.a11y.config.ts` — Playwright config dedicated to a11y.
- `frontend/tests/a11y/ui-a11y.spec.ts` — 8-scenario spec covering axe + keyboard assertions.
- `frontend/package.json` — new script `verify:a11y`.

The spec combines `@axe-core/playwright` (axe-core 4.11 under the hood) with explicit keyboard-path assertions for surfaces where axe cannot validate behavior — Dialog focus trap, Combobox keyboard navigation, etc.

## Scenarios covered (8)

| # | Scenario | Coverage type |
| --- | --- | --- |
| 1 | login page — no serious axe violations | axe-core wcag2a/aa/21a/21aa |
| 2 | workspace shell — no serious axe violations | axe-core |
| 3 | settings modal — opens, focus inside, Esc closes | keyboard path + axe |
| 4 | position dropdown — opens via click, options visible, Esc closes | keyboard path |
| 5 | settings LLM tab — Combobox focusable, ArrowDown opens, Esc closes | keyboard path + axe |
| 6 | sidebar collapse button — focusable, Enter activates | keyboard path |
| 7 | composer textarea — Ctrl+Enter / Meta+Enter do not corrupt input | keyboard path |
| 8 | no native `title=` attribute in workspace shell | UI guardrail integration |

## Severity policy

`expectNoCriticalViolations` is the primary axe gate. The harness fails only on `critical` axe violations. `serious` violations are surfaced in the test log and tracked as backlog:

| Rule | Why not failing Phase 2 | Backlog owner |
| --- | --- | --- |
| `color-contrast` (5+ hits across login, workspace, LLM tab) | Coordinated UI token adjustments; not appropriate for "建立基础可访问性验证" scope. Many of the hits involve brand-tone colors whose adjustments would change visual identity — out of `DESIGN.md` change scope. | UI token team (Phase 4+) |

The spec logs the `serious` list on every axe call so the backlog is always visible in CI output. Promote `serious` to `critical` once the contrast backlog is cleared.

## Critical violations fixed in Phase 2

The Phase 2 spec surfaced the following `button-name` critical violations and the corresponding product changes:

- `frontend/src/components/workspace/AppSidebar.vue` — session list `<button>` lacked an accessible name. Added `:aria-label` based on the session's target position so each entry is independently labeled.
- `frontend/src/components/workspace/LlmSettingsPanel.vue` — `SelectTrigger` blocks (接入方式 / 模型 / 最大回复 Token / 思考深度) had no `aria-label`. Added `aria-label` on each trigger mirroring its `FormLabel` text so the dropdown is announced correctly.

These are product-level fixes (real a11y defects) and were made without touching business logic, visual style, or DESIGN.md values.

## How to run

```powershell
npm --prefix frontend run verify:a11y
```

Output goes to:

- `frontend/test-results/` — Playwright artifacts (traces, screenshots) on failure.

## Stability guarantees

- Chromium headless, viewport 1440 × 900, DPR=1, locale `en-US`, timezone UTC, `reducedMotion: 'reduce'`.
- All `/api/**` requests are stubbed via `page.route`; no backend dependency.
- Spec runs `serial` mode (workers=1) to avoid shared-state races between route stubs.

## Known issues / backlog

| Severity | Rule | Where | Action |
| --- | --- | --- | --- |
| serious | `color-contrast` | login page (1 node), workspace shell (4), LLM tab (6) | Phase 4 token audit + brand-tone review |
| serious | `color-contrast` | settings dialog (closed) (4) | Phase 4 token audit |

These do not block Phase 2; they are tracked in `ui-phase2-baseline.md` R2.

## Out of scope

- Full WCAG 2.2 AA coverage. Only the AA subset the project ships today is checked.
- Screen reader testing (would require NVDA / VoiceOver and human-driven sessions).
- Keyboard-only flow that traverses every interactive element (only the critical paths: Dialog, Combobox, Dropdown, Tooltip, Sidebar collapse, Composer textarea).
- A11y of the `/components-lab` route introduced in Phase 3.

## CI status

- Local: PASS (8 / 8 green).
- CI: Not yet wired. Phase 5 will integrate `verify:a11y` and decide whether to gate on `critical`-only (recommended) or strict mode.
