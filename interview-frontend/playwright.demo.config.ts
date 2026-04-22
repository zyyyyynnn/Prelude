import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export default defineConfig({
  testDir: './tests',
  testMatch: 'demo-screenshots.spec.ts',
  timeout: 180000,
  fullyParallel: false,
  workers: 1,
  outputDir: path.resolve(__dirname, '../output/playwright/.artifacts'),
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5174',
    channel: 'msedge',
    headless: true,
    screenshot: 'off',
    trace: 'off',
    viewport: {
      width: 1440,
      height: 1200,
    },
  },
  webServer: {
    command: 'npm run dev:demo',
    port: 5174,
    reuseExistingServer: true,
    cwd: __dirname,
    timeout: 120000,
  },
})
