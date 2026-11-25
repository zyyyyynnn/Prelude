import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  timeout: 60_000,
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    // Pin the lockfile browser without a channel so workstation and CI rasterise identically.
    trace: {
      mode: 'retain-on-failure',
      screenshots: false,
      snapshots: false,
      sources: false,
    },
  },
  webServer: {
    command: 'npm run test:serve',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
  },
})
