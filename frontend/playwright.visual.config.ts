/**
 * Playwright config dedicated to UI visual regression captures.
 *
 * Goals (see docs/quality/ui-visual-regression.md):
 *  - Reproducible screenshots across machines: chromium headless, fixed viewport,
 *    deviceScaleFactor: 1, locale en-US, timezone UTC, reduced motion forced.
 *  - Mocked /api routes so backend dev fixtures are NOT required.
 *  - Output: tests/visual/__screenshots__/ (committed-on-demand).
 *
 * The base Playwright settings (viewport / webServer / retry policy / output
 * directory) live in `tests/_helpers/playwright-base.ts` and are spread in
 * below. This config only owns the per-scenario overrides.
 */
import { defineConfig } from '@playwright/test'
import { baseOutputDir, baseTimeouts, baseUse, baseWebServer } from './tests/_helpers/playwright-base'

export default defineConfig({
  ...baseTimeouts,
  testDir: './tests/visual',
  testMatch: 'ui-visual.spec.ts',
  outputDir: baseOutputDir('screenshots/visual/.artifacts'),
  reporter: [['list']],
  use: {
    ...baseUse,
    colorScheme: 'light',
  },
  webServer: { ...baseWebServer },
})
