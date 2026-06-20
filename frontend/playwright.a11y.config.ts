/**
 * Playwright config dedicated to UI accessibility verification.
 *
 * Goals (see docs/quality/ui-a11y.md):
 *  - Run axe-core via @axe-core/playwright on key UI surfaces (login,
 *    workspace shell, settings modal, BYOK panel, composer, analytics).
 *  - Exercise keyboard paths for Dialog / Combobox / Dropdown / Tooltip /
 *    Sidebar / Composer via real key events.
 *
 * Like the visual config, this one does NOT start a backend. Tests stub
 * /api/** routes via page.route. CI runs are gated by `npm run verify:a11y`.
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
  use: { ...baseUse },
  webServer: { ...baseWebServer },
})
