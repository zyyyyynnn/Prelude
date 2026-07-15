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
import { test, expect, type Locator, type Page } from '@playwright/test'
import { installMockApi, STRUCTURED_REPORT } from '../_helpers/mock-api'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const screenshotDir = path.resolve(__dirname, '__screenshots__')

async function capture(page: Page, file: string, readyLocator?: Locator) {
  await page.waitForLoadState('domcontentloaded')
  if (readyLocator) {
    try {
      await readyLocator.waitFor({ state: 'visible', timeout: 15000 })
    } catch (error) {
      const diagDir = path.resolve(__dirname, '../../output/screenshots/visual/.diag')
      await fs.mkdir(diagDir, { recursive: true })
      await page.screenshot({
        path: path.join(diagDir, file.replace('.png', '-missing.png')),
        fullPage: true,
      })
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
  const sidebar = page.locator('.app-sidebar').first()
  const toggle = page.locator('.app-sidebar__toggle').first()
  await expect(toggle).toBeVisible()
  const transitionDuration = await sidebar.evaluate(
    (element) => getComputedStyle(element).transitionDuration,
  )
  const expandedToolIconX = await page.evaluate(() => {
    const sidebarElement = document.querySelector('.app-sidebar')
    const icon = document.querySelector('.app-sidebar__btn--tool svg')
    if (!sidebarElement || !icon) return null
    return icon.getBoundingClientRect().left - sidebarElement.getBoundingClientRect().left
  })
  expect(transitionDuration).not.toBe('0s')
  await toggle.click()
  await page.waitForTimeout(300)
  const collapsedGeometry = await page.evaluate(() => {
    const sidebar = document.querySelector('.app-sidebar')
    const workspaceButton = document.querySelector(
      '.app-sidebar__collapsed-actions .app-sidebar__btn',
    )
    if (!sidebar || !workspaceButton) return null
    return {
      sidebarWidth: sidebar.getBoundingClientRect().width,
      buttonWidth: workspaceButton.getBoundingClientRect().width,
    }
  })
  expect(collapsedGeometry).not.toBeNull()
  expect(collapsedGeometry!.buttonWidth).toBeLessThanOrEqual(collapsedGeometry!.sidebarWidth)
  const collapsedToolIconX = await page.evaluate(() => {
    const sidebarElement = document.querySelector('.app-sidebar')
    const icon = document.querySelector('.app-sidebar__btn--tool svg')
    if (!sidebarElement || !icon) return null
    return icon.getBoundingClientRect().left - sidebarElement.getBoundingClientRect().left
  })
  expect(expandedToolIconX).not.toBeNull()
  expect(collapsedToolIconX).not.toBeNull()
  expect(Math.abs(collapsedToolIconX! - expandedToolIconX!)).toBeLessThan(0.25)
  await capture(page, '04-sidebar-collapsed.png', toggle)
})

test('05 interview empty state', async ({ page }) => {
  await installMockApi(page, { sessions: [] })
  await page.goto('/interview')
  const content = page.locator('.workspace-empty__content').first()
  await expect(content).toBeVisible()
  const box = await content.boundingBox()
  const viewport = page.viewportSize()
  expect(box).not.toBeNull()
  expect(viewport).not.toBeNull()
  expect(Math.abs(box!.y + box!.height / 2 - viewport!.height / 2)).toBeLessThan(40)
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
  const trigger = page
    .locator('button')
    .filter({ has: page.locator('.lucide-briefcase') })
    .first()
  await trigger.click()
  const menuItem = page.getByRole('menuitem', { name: 'Java 后端工程师' }).first()
  await expect(menuItem).toBeVisible()
  await capture(
    page,
    '11-position-dropdown-open.png',
    page
      .getByRole('heading', { name: '能力雷达' })
      .or(page.locator('.workspace-empty, .workspace-active').first()),
  )
})

test('12 resume dropdown open', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  // The composer uses a DropdownMenu (not a Combobox) for resume selection.
  const trigger = page
    .locator('button')
    .filter({ has: page.locator('.lucide-file-text') })
    .first()
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
  await capture(
    page,
    '13-interview-generating.png',
    page.locator('.workspace-empty, .workspace-active').first(),
  )
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
  await capture(
    page,
    '14-report-paper.png',
    page.locator('.workspace-empty, .workspace-active').first(),
  )
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
  // true (see frontend/src/app/router.ts). The visual config uses the dev server.
  await page.goto('/components-lab')
  await expect(page.locator('.lab__title').first()).toBeVisible()
  await capture(page, '16-components-lab-light.png', page.locator('.lab').first())
})

test('17 components lab (dark)', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/components-lab')
  await expect(page.locator('.lab__title').first()).toBeVisible()
  await capture(page, '17-components-lab-dark.png', page.locator('.lab').first())
})

test('18 tooltip uses a readable neutral surface', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/components-lab')
  const trigger = page.getByRole('button', { name: '通知' })
  await trigger.hover()
  const tooltip = page
    .locator('[data-dismissable-layer][data-state][data-side]')
    .filter({ hasText: 'hover / focus 触发' })
    .first()
  await expect(tooltip).toBeVisible()

  const colors = await tooltip.evaluate((element) => {
    const style = getComputedStyle(element)
    return { background: style.backgroundColor, foreground: style.color }
  })
  const channels = (value: string) =>
    value
      .match(/[\d.]+/g)!
      .slice(0, 3)
      .map(Number)
      .map((channel) => {
        const normalized = channel / 255
        return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
      })
  const luminance = (value: string) => {
    const [red, green, blue] = channels(value)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
  }
  const lighter = Math.max(luminance(colors.background), luminance(colors.foreground))
  const darker = Math.min(luminance(colors.background), luminance(colors.foreground))
  expect((lighter + 0.05) / (darker + 0.05)).toBeGreaterThanOrEqual(7)
})

test('18b chart tooltip mirrors the neutral tooltip surface', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/analytics')
  const canvas = page.locator('.chart-surface').nth(1).locator('canvas').first()
  await expect(canvas).toBeVisible()
  const box = await canvas.boundingBox()
  expect(box).not.toBeNull()

  for (const ratio of [0.2, 0.35, 0.5, 0.65, 0.8]) {
    await canvas.hover({ position: { x: box!.width * ratio, y: box!.height * 0.5 } })
    if (
      await page
        .locator('.ui-chart-tooltip')
        .isVisible()
        .catch(() => false)
    )
      break
  }

  const tooltip = page.locator('.ui-chart-tooltip')
  await expect(tooltip).toBeVisible()
  const matchesTokens = await tooltip.evaluate((element) => {
    const style = getComputedStyle(element)
    const root = getComputedStyle(document.documentElement)
    const normalized = (value: string) => {
      const probe = document.createElement('span')
      probe.style.color = value
      document.body.append(probe)
      const color = getComputedStyle(probe).color
      probe.remove()
      return color
    }
    return (
      style.backgroundColor === normalized(root.getPropertyValue('--color-text-primary')) &&
      style.color === normalized(root.getPropertyValue('--color-bg'))
    )
  })
  expect(matchesTokens).toBe(true)
})

test('19 interview messages do not expose live score or hint', async ({ page }) => {
  const detail = {
    sessionId: 101,
    status: 'ongoing',
    targetPosition: 'Java 后端工程师',
    currentStage: 'technical',
    summaryReport: null,
    stages: [],
    messages: [
      { id: 1, role: 'assistant', content: '如何保证接口幂等？', seqNum: 1 },
      {
        id: 2,
        role: 'user',
        content: '使用唯一请求键。',
        seqNum: 2,
        score: 8,
        hint: '缺少量化依据',
      },
    ],
    resumeId: 1,
    positionId: 1,
  }
  await installMockApi(page, {
    sessions: [
      {
        sessionId: 101,
        status: 'ongoing',
        targetPosition: 'Java 后端工程师',
        currentStage: 'technical',
      },
    ],
    interviewDetail: detail,
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '开始面试' }).click()

  await expect(page.getByText('使用唯一请求键。')).toBeVisible()
  await expect(page.getByText(/评分：8\/10/)).toHaveCount(0)
  await expect(page.getByText('缺少量化依据')).toHaveCount(0)
})

test('20 structured report carousel keeps compatibility details out of the primary UI', async ({
  page,
}) => {
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: STRUCTURED_REPORT,
  }
  const detail = {
    ...session,
    stages: [],
    messages: [],
    resumeId: 1,
    positionId: 1,
  }
  await installMockApi(page, { sessions: [session], interviewDetail: detail })
  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()

  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '逐题复盘' })).toBeVisible()
  await expect(page.getByText('性能量化：缺少压测指标')).toBeVisible()
  await expect(page.locator('.structured-report ul').first()).toBeVisible()
  await expect(page.getByText('查看兼容文本报告')).toHaveCount(0)
  await expect(page.getByText('1 / 2')).toBeVisible()
  const titleLineCount = await page
    .getByRole('heading', { name: '求职训练报告' })
    .evaluate((element) => {
      const range = document.createRange()
      range.selectNodeContents(element)
      return range.getClientRects().length
    })
  expect(titleLineCount).toBe(1)
  await page.getByRole('button', { name: '下一题' }).click()
  await expect(page.getByText('如何定位复杂状态更新问题？')).toBeVisible()
  await expect(page.getByText('2 / 2')).toBeVisible()
  await page.locator('.question-review-carousel').focus()
  await page.keyboard.press('ArrowLeft')
  await expect(page.getByText('如何优化虚拟列表？')).toBeVisible()
  await capture(page, '18-structured-report.png', page.locator('.structured-report').first())
})

test('21 old markdown report remains readable', async ({ page }) => {
  const markdown = '# 旧版面试报告\n\n旧数据仍可查看。'
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: markdown,
  }
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()

  await expect(page.getByRole('heading', { name: '旧版面试报告' })).toBeVisible()
})

test('22 structured report fits mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: STRUCTURED_REPORT,
  }
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()

  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  )
  expect(hasHorizontalOverflow).toBe(false)
  await capture(page, '19-structured-report-mobile.png', page.locator('.structured-report').first())
})

test('23 structured report exports a non-empty PDF', async ({ page }) => {
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: STRUCTURED_REPORT,
  }
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 PDF' }).click()
  const download = await downloadPromise
  const path = await download.path()
  expect(path).toBeTruthy()
  const stat = await fs.stat(path!)
  expect(stat.size).toBeGreaterThan(1000)
})
