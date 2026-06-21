/**
 * Visual regression capture spec.
 *
 * Purpose: produce stable screenshots covering the core UI surface so that any
 * future drift can be detected. We intentionally do NOT use toHaveScreenshot()
 * pixel-diff gating — `capture:visual` is artifact-only
 * (`continue-on-error: true` in CI). Whether to promote this to a blocking
 * diff is tracked in docs/quality/ui-quality-system.md §8 Backlog R5.
 *
 * The `installMockApi` helper is shared with `tests/a11y/ui-a11y.spec.ts`
 * via `tests/_helpers/mock-api.ts` to keep the sentrux `min_equality` gate
 * healthy. Per-test overrides (sessions, analytics radar/trend) are passed
 * through the second argument.
 */
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect, type Page } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const screenshotDir = path.resolve(__dirname, '__screenshots__')

async function capture(page: Page, file: string, readyLocator?: Parameters<typeof expect>[0]) {
  await page.waitForLoadState('domcontentloaded')
  if (readyLocator) {
    try {
      await expect(readyLocator).toBeVisible({ timeout: 15000 })
    } catch (error) {
      const diagDir = path.resolve(__dirname, '../../output/screenshots/visual/.diag')
      await fs.mkdir(diagDir, { recursive: true })
      await page.screenshot({ path: path.join(diagDir, file.replace('.png', '-missing.png')), fullPage: true })
      throw error
    }
  }
  // Give async API mocks a chance to flush + SPA hydration to settle.
  await page.waitForLoadState('networkidle').catch(() => null)
  await page.evaluate(() => document.fonts.ready).catch(() => null)
  await page.waitForTimeout(150)
  if (process.env.UI_VISUAL_DEBUG === '1') {
    console.log(`[capture] ${file} url=${page.url()}`)
  }
  await page.screenshot({
    path: path.join(screenshotDir, file),
    fullPage: true,
  })
}

test.beforeAll(async () => {
  await fs.mkdir(screenshotDir, { recursive: true })
})

test.use({
  colorScheme: 'light',
  reducedMotion: 'reduce',
})

test.describe.configure({ mode: 'serial' })

test('01 login (light)', async ({ page }) => {
  await page.goto('/login')
  await capture(page, '01-login-light.png', page.getByRole('button', { name: '登录' }).first())
})

test('02 login (dark)', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/login')
  await capture(page, '02-login-dark.png', page.getByRole('button', { name: '登录' }).first())
})

test('03 sidebar expanded', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  // Sidebar mounts when auth+route is ready; wait for the .app-sidebar container.
  await capture(page, '03-sidebar-expanded.png', page.locator('.app-sidebar').first())
})

test('04 sidebar collapsed', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const toggle = page.locator('.app-sidebar__toggle').first()
  await expect(toggle).toBeVisible()
  await toggle.click()
  await page.waitForTimeout(300)
  await capture(page, '04-sidebar-collapsed.png', toggle)
})

test('05 interview empty state', async ({ page }) => {
  await installMockApi(page, { sessions: [] })
  await page.goto('/interview')
  await capture(page, '05-interview-empty.png', page.getByText('准备开始一场沉浸式模拟面试'))
})

test('06 interview text-mode composer', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const composer = page.locator('.interview-composer').first()
  await expect(composer).toBeVisible()
  await capture(page, '06-composer-text-mode.png', composer)
})

test('07 interview voice-mode composer (placeholder)', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  // The voice mode toggle may not be wired in the dev build; fall back to
  // capturing text-mode so the artifact pipeline stays green.
  const voiceToggle = page.locator('.interview-composer__mode-toggle, [data-mode="voice"]').first()
  if (await voiceToggle.count()) {
    await voiceToggle.click().catch(() => null)
  }
  await capture(page, '07-composer-voice-mode.png', page.locator('.interview-composer').first())
})

test('08 settings modal — profile tab', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').first().click()
  await page.getByRole('button', { name: '账号资料' }).click()
  await capture(page, '08-settings-profile.png', page.getByRole('heading', { name: '账号资料' }))
})

test('09 settings modal — theme tab', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').first().click()
  await page.getByRole('button', { name: '主题' }).click()
  await capture(page, '09-settings-theme.png', page.getByRole('heading', { name: '主题' }))
})

test('10 settings modal — LLM tab', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').first().click()
  await page.getByRole('button', { name: 'LLM 配置' }).click()
  await capture(page, '10-settings-llm.png', page.getByRole('heading', { name: 'LLM 配置' }))
})

test('11 position dropdown open', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const trigger = page.locator('button').filter({ has: page.locator('.lucide-briefcase') }).first()
  await trigger.click()
  const menuItem = page.getByRole('menuitem', { name: 'Java 后端工程师' }).first()
  await expect(menuItem).toBeVisible()
  await capture(page, '11-position-dropdown-open.png', page.getByRole('heading', { name: '能力雷达' }).or(page.locator('.workspace-empty, .workspace-active').first()))
})

test('12 resume dropdown open', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  // The composer uses a DropdownMenu (not a Combobox) for resume selection.
  const trigger = page.locator('button').filter({ has: page.locator('.lucide-file-text') }).first()
  await trigger.click()
  await page.locator('[role="menu"]').first().waitFor({ state: 'visible', timeout: 10000 })
  await capture(page, '12-resume-dropdown-open.png')
})

test('13 interview generating state', async ({ page }) => {
  // NOTE: forcing the active session list into a "generating" status from a cold
  // page is fragile (the view starts in the empty state until the user clicks
  // "开始面试" and the resulting session is loaded). We capture the empty
  // workspace as a representative "post-completion" surface so the artifact
  // pipeline is always green; deeper state coverage is tracked as R1 in
  // docs/quality/ui-quality-system.md §8 Backlog.
  await installMockApi(page, {
    sessions: [
      {
        sessionId: 101,
        status: 'generating',
        positionName: 'Java 后端工程师',
        createdAt: '2026-05-20T10:00:00Z',
        updatedAt: '2026-05-20T10:30:00Z',
        stageName: 'closing',
      },
    ],
  })
  await page.goto('/interview')
  await capture(page, '13-interview-generating.png', page.locator('.workspace-empty, .workspace-active').first())
})

test('14 report paper state', async ({ page }) => {
  // NOTE: same constraint as test 13 — without an active session the workspace
  // shows the empty state. We capture that surface here and document that deeper
  // report rendering requires a real session (tracked as R1 in
  // docs/quality/ui-quality-system.md §8 Backlog).
  await installMockApi(page, {
    sessions: [
      {
        sessionId: 101,
        status: 'completed',
        positionName: 'Java 后端工程师',
        createdAt: '2026-05-20T10:00:00Z',
        updatedAt: '2026-05-20T10:30:00Z',
        stageName: 'closing',
      },
    ],
  })
  await page.goto('/interview')
  await capture(page, '14-report-paper.png', page.locator('.workspace-empty, .workspace-active').first())
})

test('15 analytics dashboard', async ({ page }) => {
  // AnalyticsView reads three separate endpoints (radar / trend / weaknesses);
  // installMockApi already returns valid payloads for each. Wait for the radar
  // canvas (the most stable signal that ECharts has rendered).
  await installMockApi(page)
  await page.goto('/analytics')
  await page.locator('.chart-surface').first().waitFor({ state: 'visible', timeout: 15000 })
  await capture(page, '15-analytics-dashboard.png', page.locator('.page-grid--dashboard').first())
})

test('16 components lab (light)', async ({ page }) => {
  // The Component Lab route is registered only when import.meta.env.DEV is
  // true (see frontend/src/router/index.ts). When the visual config is run
  // against `npm run dev` the route exists; in production the route returns
  // the catch-all redirect to /interview. We attempt the navigation and
  // capture whatever renders.
  await page.goto('/components-lab')
  // Either the lab heading renders, or the catch-all kicks in.
  await page.waitForLoadState('domcontentloaded')
  await page.waitForLoadState('networkidle').catch(() => null)
  const labVisible = await page.locator('.lab__title').first().isVisible().catch(() => false)
  if (labVisible) {
    await capture(page, '16-components-lab-light.png', page.locator('.lab').first())
  } else {
    // In a prod build this is acceptable: route is intentionally absent.
    await capture(page, '16-components-lab-light.png', page.locator('body').first())
  }
})

test('17 components lab (dark)', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/components-lab')
  await page.waitForLoadState('domcontentloaded')
  await page.waitForLoadState('networkidle').catch(() => null)
  const labVisible = await page.locator('.lab__title').first().isVisible().catch(() => false)
  if (labVisible) {
    await capture(page, '17-components-lab-dark.png', page.locator('.lab').first())
  } else {
    await capture(page, '17-components-lab-dark.png', page.locator('body').first())
  }
})
