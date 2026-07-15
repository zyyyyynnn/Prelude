import { defineConfig } from '@playwright/test'
import { baseTimeouts, baseUse, baseWebServer } from './tests/_helpers/playwright-base'

export default defineConfig({
  ...baseTimeouts,
  testDir: './tests/flows',
  testMatch: '*.spec.ts',
  reporter: [['list']],
  use: {
    ...baseUse,
    colorScheme: 'light',
  },
  webServer: { ...baseWebServer },
})
