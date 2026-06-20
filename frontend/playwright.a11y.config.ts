import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

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
 */
export default defineConfig({
  testDir: './tests/a11y',
  testMatch: 'ui-a11y.spec.ts',
  timeout: 120000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
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
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5173',
    port: 5173,
    reuseExistingServer: !process.env.CI,
    cwd: __dirname,
    timeout: 120000,
  },
})
