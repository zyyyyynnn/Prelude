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
    /* No `channel`: the pixel oracle has to run on the browser whose version the lockfile
       names. `channel: 'msedge'` resolved to whatever Edge the host happened to have, so a
       runner-image refresh or a workstation update changed native painting — the textarea's
       resize grip alone accounted for every differing pixel in the first CI run — and the
       baselines went stale with no product change and no explanation. */
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
