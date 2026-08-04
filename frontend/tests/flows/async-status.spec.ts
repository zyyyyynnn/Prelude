import { expect, test } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

test('keeps analytics failure explicit and retries without a fake empty state', async ({
  page,
}) => {
  let attempts = 0
  await installMockApi(page)
  await page.route('**/api/analytics/radar', (route) => {
    attempts += 1
    if (attempts === 1) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 503, message: 'analytics unavailable', data: null }),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: { technical: 7, expression: 8, logic: 7, sessionCount: 3 },
      }),
    })
  })

  await page.goto('/analytics')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByText('暂无历史面试数据，完成至少一场面试后再回来查看。')).toHaveCount(0)

  await page.getByRole('button', { name: '重试' }).click()
  await expect(page.getByText('能力雷达')).toBeVisible()
  expect(attempts).toBe(2)
})
