/**
 * Playwright config dedicated to local screenshot capture (developer only,
 * NOT part of CI). Reuses the same Vite dev server as the visual / a11y
 * configs but writes to output/screenshots/dev/.artifacts/ and uses the
 * system browser (msedge) at 2x DPR with zh-CN locale. Tests stub /api
 * routes via page.route.
 *
 * The base Playwright settings live in `tests/_helpers/playwright-base.ts`
 * and are spread in below. This config only owns the developer-machine
 * overrides (channel / viewport height / locale / deviceScaleFactor).
 */
import { defineConfig } from '@playwright/test'
import {
  baseOutputDir,
  baseTimeouts,
  baseUse,
  baseWebServer,
} from './tests/_helpers/playwright-base'

export default defineConfig({
  ...baseTimeouts,
  testDir: './tests',
  testMatch: 'local-screenshots.spec.ts',
  timeout: 180000,
  outputDir: baseOutputDir('screenshots/dev/.artifacts'),
  reporter: [['list']],
  use: {
    ...baseUse,
    channel: 'msedge',
    screenshot: 'off',
    trace: 'off',
    viewport: { width: 1440, height: 1200 },
    deviceScaleFactor: 2,
    locale: 'zh-CN',
  },
  webServer: { ...baseWebServer, command: 'npm run dev -- --host 127.0.0.1' },
})
