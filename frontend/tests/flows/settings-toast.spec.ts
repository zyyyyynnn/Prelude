import { expect, test } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

test('closes a settings toast without dismissing the settings dialog', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')

  await page.locator('.app-sidebar__btn--settings').click()
  const settingsDialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(settingsDialog).toBeVisible()

  await page.getByRole('button', { name: 'LLM 配置' }).click()
  await page.getByRole('button', { name: '测试连接' }).click()
  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '模型配置测试通过' })
  const closeButton = toast.getByRole('button', { name: '关闭系统提示' })
  await expect(closeButton).toBeVisible()

  const geometry = await toast.evaluate((toastElement) => {
    const closeElement = toastElement.querySelector<HTMLElement>('[data-close-button]')
    if (!closeElement) {
      return null
    }

    const toastRect = toastElement.getBoundingClientRect()
    const closeRect = closeElement.getBoundingClientRect()
    const closeStyle = getComputedStyle(closeElement)
    const rootStyle = getComputedStyle(document.documentElement)
    const shadowColors = [...closeStyle.boxShadow.matchAll(/rgba?\(([^)]+)\)/g)]
    const hasVisibleShadow =
      closeStyle.boxShadow !== 'none' &&
      (shadowColors.length === 0 ||
        shadowColors.some(([, channels]) => {
          const slashAlpha = channels.match(/\/\s*([\d.]+)/)
          if (slashAlpha) return Number.parseFloat(slashAlpha[1]) > 0
          const components = channels.split(',').map((value) => value.trim())
          return components.length < 4 || Number.parseFloat(components[3]) > 0
        }))

    return {
      centerDelta: Math.abs(
        closeRect.top + closeRect.height / 2 - (toastRect.top + toastRect.height / 2),
      ),
      rightInset: toastRect.right - closeRect.right,
      expectedRightInset: Number.parseFloat(rootStyle.getPropertyValue('--spacing-sm')),
      isInRightHalf: closeRect.left >= toastRect.left + toastRect.width / 2,
      borderWidth: closeStyle.borderWidth,
      hasVisibleShadow,
    }
  })

  expect(geometry).not.toBeNull()
  expect(geometry?.centerDelta).toBeLessThanOrEqual(1)
  expect(
    Math.abs((geometry?.rightInset ?? 0) - (geometry?.expectedRightInset ?? 0)),
  ).toBeLessThanOrEqual(1)
  expect(geometry?.isInRightHalf).toBe(true)
  expect(geometry?.borderWidth).toBe('1px')
  expect(geometry?.hasVisibleShadow).toBe(false)

  await closeButton.click()
  await expect(toast).toHaveCount(0)
  await expect(settingsDialog).toBeVisible()
})

test('keeps the LLM settings request cached while switching settings tabs', async ({ page }) => {
  await installMockApi(page)
  let providerRequests = 0
  let configRequests = 0
  page.on('request', (request) => {
    if (request.method() !== 'GET') return
    if (request.url().endsWith('/api/llm/providers')) providerRequests += 1
    if (request.url().endsWith('/api/user/llm-config')) configRequests += 1
  })
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const settingsDialog = page.getByRole('dialog', { name: '全局设置' })

  await expect.poll(() => providerRequests).toBe(1)
  await expect.poll(() => configRequests).toBe(1)
  await settingsDialog.getByRole('button', { name: 'LLM 配置' }).click()
  await expect(settingsDialog.getByRole('button', { name: '测试连接' })).toBeEnabled()
  await expect.poll(() => providerRequests).toBe(2)
  await expect.poll(() => configRequests).toBe(2)

  await settingsDialog.getByRole('button', { name: '账号资料' }).click()
  await expect(settingsDialog.locator('#profile-username')).toHaveValue('demo')
  await settingsDialog.getByRole('button', { name: 'LLM 配置' }).click()
  await expect(settingsDialog.getByRole('button', { name: '测试连接' })).toBeEnabled()
  expect(providerRequests).toBe(2)
  expect(configRequests).toBe(2)
})
