import { expect, test } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

test('closes a settings toast without dismissing the settings dialog', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')

  await page.locator('.app-sidebar__btn--settings').click()
  const settingsDialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(settingsDialog).toBeVisible()

  await page.getByRole('button', { name: 'LLM 配置' }).click()
  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '配置已加载' })
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
    return {
      centerDelta: Math.abs(
        closeRect.top + closeRect.height / 2 - (toastRect.top + toastRect.height / 2),
      ),
      isInsideRightEdge: closeRect.right <= toastRect.right && closeRect.left >= toastRect.left,
      borderWidth: closeStyle.borderWidth,
    }
  })

  expect(geometry).not.toBeNull()
  expect(geometry?.centerDelta).toBeLessThanOrEqual(1)
  expect(geometry?.isInsideRightEdge).toBe(true)
  expect(geometry?.borderWidth).toBe('0px')

  await closeButton.click()
  await expect(toast).toHaveCount(0)
  await expect(settingsDialog).toBeVisible()
})
