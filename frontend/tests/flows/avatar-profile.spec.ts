import { expect, test } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

const ONE_PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)

test('uploads an avatar through a local preview and settles on the canonical media URL', async ({
  page,
}) => {
  await installMockApi(page)
  await page.route('**/media/avatars/42-playwright-avatar.png', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: ONE_PIXEL_PNG }),
  )
  await page.goto('/interview')

  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(dialog).toBeVisible()
  await expect(dialog.locator('#profile-username')).toHaveValue('demo')

  await dialog.locator('#profile-avatar-input').setInputFiles({
    name: 'avatar.svg',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })

  const avatar = dialog.locator('.user-avatar')
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-loaded')
  await expect(avatar.locator('img')).toHaveAttribute(
    'src',
    '/media/avatars/42-playwright-avatar.png',
  )
  await expect(avatar.locator('img')).not.toHaveAttribute('src', /^blob:/)
})
