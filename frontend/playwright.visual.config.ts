/**
 * Playwright config dedicated to UI visual regression captures.
 *
 * Goals (see docs/quality/ui-quality-system.md §4 Visual coverage):
 *  - Reproducible screenshots: local default Playwright browser (chromium
 *    headless), fixed viewport, deviceScaleFactor: 1, locale en-US,
 *    timezone UTC, reduced motion forced.
 *  - In CI: shared baseUse injects `channel: 'msedge'` and
 *    `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` keeps the system Microsoft Edge
 *    channel without downloading Playwright Chromium.
 *  - Mocked /api routes so backend dev fixtures are NOT required.
 *  - Output: tests/visual/__screenshots__/ (committed-on-demand).
 *  - Current policy: `capture:visual` is artifact-only
 *    (`continue-on-error: true` in CI), not a blocking pixel-diff gate.
 *    Promotion to blocking is tracked as R5 in
 *    docs/quality/ui-quality-system.md §8 Backlog.
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
