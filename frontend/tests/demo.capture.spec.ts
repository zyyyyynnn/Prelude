import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page } from '@playwright/test'
import {
  createDemoState,
  DEMO_VIEWPORT,
  installDemoHarness,
} from './demo-harness'

const screenshotDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../output/screenshots/demo',
)

test.use({ viewport: DEMO_VIEWPORT, deviceScaleFactor: 1 })

test('@smoke @demo captures the deterministic React demo chain', async ({ page }) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mkdir(screenshotDirectory, { recursive: true })

  await page.goto('/login')
  await expect(page.getByRole('heading', { level: 1, name: '进入面试工作台' })).toBeVisible()
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect.poll(() => state.requests.some(({ path }) => path === '/api/auth/login')).toBe(true)
  await expect(page).toHaveURL(/\/interview$/)

  await selectContext(page, '选择简历', 'Java 后端候选人简历.pdf')
  await selectContext(page, '选择岗位', 'Java 后端工程师')
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page).toHaveURL(/session=42/)
  await expect(page.getByText('请结合实际项目，说明你会如何拆分高并发订单系统的服务边界。')).toBeVisible()

  await page.getByLabel('面试回答').fill(
    '我会按订单、库存和履约能力拆分边界，通过事件驱动降低同步耦合，并为关键链路设置幂等与补偿。',
  )
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.locator('.workspace-header .status-badge')).toHaveText('收尾')
  await expect(page.getByText('边界分析很清楚。最后请总结你会如何验证容量目标与故障恢复能力。')).toBeVisible()
  await expectActiveInterviewGeometry(page)
  await expectNoCriticalAccessibilityViolations(page)
  await capture(page, 'active-interview.png')

  await page.getByRole('button', { name: '生成报告' }).click()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await expect(page.getByText('8.5')).toBeVisible()
  await expectStructuredReportGeometry(page)
  await expectNoCriticalAccessibilityViolations(page)
  await capture(page, 'structured-report.png')

  await page.getByRole('link', { name: '数据看板' }).click()
  await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '分数趋势' })).toBeVisible()
  await expect(page.getByText('容量估算')).toBeVisible()
  await expectAnalyticsGeometry(page)
  await expectNoCriticalAccessibilityViolations(page)
  await capture(page, 'populated-analytics.png')

  await page.getByRole('button', { name: '设置' }).click()
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeVisible()
  await expect(page.getByLabel('用户名')).toHaveValue('demo')
  await expect(page.getByLabel('邮箱')).toHaveValue('demo@prelude.local')
  await expectSettingsGeometry(page)
  await expectNoCriticalAccessibilityViolations(page)
  await capture(page, 'global-settings.png')

  const requestedPaths = new Set(state.requests.map((request) => request.path))
  for (const requiredPath of [
    '/api/auth/me',
    '/api/auth/login',
    '/api/interview/sessions',
    '/api/resume/list',
    '/api/position/list',
    '/api/llm/providers',
    '/api/llm/config',
    '/api/interview/start',
    '/api/interview/42/messages',
    '/api/interview/42/chat',
    '/api/interview/42/finish',
    '/api/analytics/radar',
    '/api/analytics/trend',
    '/api/analytics/weaknesses',
    '/api/user/profile',
  ]) {
    expect(requestedPaths.has(requiredPath), `演示链路缺少 ${requiredPath}`).toBe(true)
  }
})

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}

async function capture(page: Page, fileName: string) {
  await settle(page)
  await page.screenshot({
    path: path.join(screenshotDirectory, fileName),
    fullPage: false,
  })
}

async function settle(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise<void>((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
    )
    await Promise.all(
      Array.from(document.getAnimations())
        .filter((animation) => animation.playState !== 'finished')
        .map((animation) => animation.finished.catch(() => undefined)),
    )
  })
}

async function expectActiveInterviewGeometry(page: Page) {
  const header = await visibleBox(page.locator('.workspace-header'))
  const thread = await visibleBox(page.locator('.message-thread'))
  const composer = await visibleBox(page.locator('.workspace-composer-fixed'))
  expect(header.bottom).toBeLessThanOrEqual(thread.top + 1)
  expect(composer.top).toBeGreaterThan(header.bottom)
  expect(composer.bottom).toBeLessThanOrEqual(DEMO_VIEWPORT.height + 1)
  expect(thread.width).toBeGreaterThan(DEMO_VIEWPORT.width / 2)
}

async function expectStructuredReportGeometry(page: Page) {
  const viewport = await visibleBox(page.locator('.workspace-report'))
  const report = await visibleBox(page.locator('.structured-report'))
  expect(report.left).toBeGreaterThanOrEqual(viewport.left)
  expect(report.right).toBeLessThanOrEqual(viewport.right + 1)
  expect(report.width).toBeGreaterThan(600)
}

async function expectAnalyticsGeometry(page: Page) {
  const panels = page.locator('.analytics-dashboard-grid > .analytics-panel')
  await expect(panels).toHaveCount(2)
  const radar = await visibleBox(panels.nth(0))
  const trend = await visibleBox(panels.nth(1))
  expect(Math.abs(radar.top - trend.top)).toBeLessThanOrEqual(1)
  expect(radar.right).toBeLessThanOrEqual(trend.left)
  expect(radar.width).toBeGreaterThan(300)
  expect(trend.width).toBeGreaterThan(300)
}

async function expectSettingsGeometry(page: Page) {
  const dialog = await visibleBox(page.getByRole('dialog', { name: '全局设置' }))
  const sidebar = await visibleBox(page.locator('.settings-sidebar'))
  const main = await visibleBox(page.locator('.settings-main'))
  expect(dialog.left).toBeGreaterThanOrEqual(0)
  expect(dialog.right).toBeLessThanOrEqual(DEMO_VIEWPORT.width + 1)
  expect(dialog.bottom).toBeLessThanOrEqual(DEMO_VIEWPORT.height + 1)
  expect(sidebar.right).toBeLessThanOrEqual(main.left + 1)
}

async function visibleBox(locator: Locator) {
  await expect(locator).toBeVisible()
  const box = await locator.boundingBox()
  expect(box).not.toBeNull()
  return {
    left: box!.x,
    right: box!.x + box!.width,
    top: box!.y,
    bottom: box!.y + box!.height,
    width: box!.width,
    height: box!.height,
  }
}

async function expectNoCriticalAccessibilityViolations(page: Page) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  expect(result.violations.filter((violation) => violation.impact === 'critical')).toEqual([])
}
