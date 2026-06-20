import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

/**
 * Playwright config dedicated to UI visual regression captures.
 *
 * Goals (see docs/quality/ui-visual-regression.md):
 *  - Reproducible screenshots across machines: chromium headless, fixed viewport,
 *    deviceScaleFactor: 1, locale en-US, timezone UTC, reduced motion forced.
 *  - Mocked /api routes so backend dev fixtures are NOT required.
 *  - Output: tests/visual/__screenshots__/ (committed-on-demand).
 *
 * Note: this config does NOT start a backend. The tests stub /api requests
 * via page.route, so the spec is CI-runnable without Spring Boot.
 */
export default defineConfig({
  testDir: './tests/visual',
  testMatch: 'ui-visual.spec.ts',
  timeout: 120000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  outputDir: path.resolve(__dirname, '../../output/screenshots/visual/.artifacts'),
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    viewport: {
      width: 1440,
      height: 900,
    },
    deviceScaleFactor: 1,
    locale: 'en-US',
    timezoneId: 'UTC',
    colorScheme: 'light',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5173',
    port: 5173,
    reuseExistingServer: !process.env.CI,
    cwd: __dirname,
    timeout: 120000,
  },
})
