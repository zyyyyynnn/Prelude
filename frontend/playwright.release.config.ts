import { defineConfig } from '@playwright/test'

const runtime = globalThis as typeof globalThis & {
  process?: { env?: Record<string, string | undefined> }
}

export default defineConfig({
  testDir: './tests',
  testMatch: 'release.spec.ts',
  fullyParallel: false,
  forbidOnly: Boolean(runtime.process?.env?.CI),
  workers: 1,
  retries: 0,
  timeout: 180_000,
  reporter: [
    ['json', { outputFile: 'output/release-smoke/results.json' }],
    ['junit', { outputFile: 'output/release-smoke/results.xml' }],
    ['html', { outputFolder: 'output/release-smoke/html', open: 'never' }],
    ['line'],
  ],
  use: {
    baseURL: runtime.process?.env?.RELEASE_BASE_URL || 'http://127.0.0.1:14173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
})
