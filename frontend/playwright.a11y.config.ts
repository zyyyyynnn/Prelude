/**
 * Playwright config dedicated to UI accessibility verification.
 *
 * Goals (see docs/quality/ui-quality-system.md §5 A11y coverage):
 *  - Run axe-core via @axe-core/playwright on key UI surfaces (login,
 *    workspace shell, settings modal, BYOK panel, composer, analytics).
 *  - Exercise keyboard paths for Dialog / Combobox / Dropdown / Tooltip /
 *    Sidebar / Composer via real key events.
 *  - Severity policy: only CRITICAL axe violations block the run. SERIOUS
 *    violations (notably color-contrast) are reported to the console and
 *    tracked as backlog; a green run means "no critical axe violations
 *    are blocking", not "no a11y issues".
 *
 * Like the visual config, this one does NOT start a backend. Tests stub
 * /api/** routes via page.route. CI runs are gated by `npm run verify:a11y`
 * (blocking).
 *
 * The base Playwright settings (viewport / webServer / retry policy) live
 * in `tests/_helpers/playwright-base.ts` and are spread in below. This
 * config only owns the per-scenario overrides.
 */
import { defineConfig } from '@playwright/test'
import { baseTimeouts, baseUse, baseWebServer } from './tests/_helpers/playwright-base'

export default defineConfig({
  ...baseTimeouts,
  testDir: './tests/a11y',
  testMatch: 'ui-a11y.spec.ts',
  reporter: [['list']],
  use: {
    ...baseUse,
    colorScheme: 'light',
  },
  webServer: { ...baseWebServer },
})
