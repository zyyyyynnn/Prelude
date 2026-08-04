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
  await page.addInitScript(() => {
    const revoked = [] as string[]
    Object.defineProperty(window, '__preludeRevokedAvatarUrls', {
      configurable: true,
      value: revoked,
    })
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.revokeObjectURL = (url) => {
      revoked.push(url)
      revoke(url)
    }
  })
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
  await expect(avatar.locator('img')).toHaveAttribute('alt', '当前用户头像')
  await expect(avatar.locator('img')).not.toHaveAttribute('src', /^blob:/)
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as Window & { __preludeRevokedAvatarUrls?: string[] }).__preludeRevokedAvatarUrls
            ?.length ?? 0,
      ),
    )
    .toBeGreaterThan(0)
  expect(
    await page.evaluate(
      () =>
        (window as Window & { __preludeRevokedAvatarUrls?: string[] })
          .__preludeRevokedAvatarUrls?.[0],
    ),
  ).toMatch(/^blob:/)
})

test('keeps fixed avatar geometry while the canonical image is loading', async ({ page }) => {
  await installMockApi(page, {
    userProfile: { username: 'loading-user', avatarUrl: '/media/avatars/delayed.png' },
  })
  let releaseMedia!: () => void
  const mediaGate = new Promise<void>((resolve) => {
    releaseMedia = resolve
  })
  let mediaStartedResolve!: () => void
  const mediaStarted = new Promise<void>((resolve) => {
    mediaStartedResolve = resolve
  })
  await page.route('**/media/avatars/delayed.png', async (route) => {
    mediaStartedResolve()
    await mediaGate
    await route.fulfill({ status: 200, contentType: 'image/png', body: ONE_PIXEL_PNG })
  })
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  const avatar = dialog.locator('.user-avatar')
  await mediaStarted
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-loading')
  await expect(avatar.locator('img')).toHaveCount(0)
  await expect(avatar.locator('.user-avatar__initials')).toHaveCount(0)
  await expect(avatar).toHaveAttribute('aria-busy', 'true')

  const before = await avatar.boundingBox()
  releaseMedia()
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-loaded')
  await expect(avatar.locator('img')).toHaveAttribute('src', '/media/avatars/delayed.png')
  const after = await avatar.boundingBox()
  expect(after?.width).toBe(before?.width)
  expect(after?.height).toBe(before?.height)
})

test('falls back to initials and inline warning when the canonical avatar is unavailable', async ({
  page,
}) => {
  await installMockApi(page, {
    userProfile: { username: 'broken-avatar', avatarUrl: '/media/avatars/missing.png' },
  })
  await page.route('**/media/avatars/missing.png', (route) => route.fulfill({ status: 404 }))
  await page.goto('/interview')

  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  const avatar = dialog.locator('.user-avatar')
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-error')
  await expect(avatar.locator('img')).toHaveCount(0)
  await expect(avatar.locator('.user-avatar__initials')).toHaveText('B')
  await expect(dialog.getByRole('alert')).toContainText('头像文件暂不可用')
})

test('treats canonical decode failure as an image error instead of a false success', async ({
  page,
}) => {
  await installMockApi(page, {
    userProfile: { username: 'decode-failure', avatarUrl: '/media/avatars/decode.png' },
  })
  await page.addInitScript(() => {
    HTMLImageElement.prototype.decode = () => Promise.reject(new Error('decode failed'))
  })
  await page.route('**/media/avatars/decode.png', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: ONE_PIXEL_PNG }),
  )
  await page.goto('/interview')

  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  const avatar = dialog.locator('.user-avatar')
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-error')
  await expect(avatar.locator('img')).toHaveCount(0)
  await expect(avatar.locator('.user-avatar__initials')).toHaveText('D')
})

test('keeps the profile skeleton visible while the profile request is pending', async ({
  page,
}) => {
  await installMockApi(page)
  let releaseProfile!: () => void
  const profileGate = new Promise<void>((resolve) => {
    releaseProfile = resolve
  })
  let profileStartedResolve!: () => void
  const profileStarted = new Promise<void>((resolve) => {
    profileStartedResolve = resolve
  })
  await page.route('**/api/user/profile', async (route) => {
    profileStartedResolve()
    await profileGate
    await route.fulfill({
      json: {
        code: 200,
        message: 'ok',
        data: { username: 'pending-profile', email: 'pending@example.com', avatarUrl: '' },
      },
    })
  })
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await profileStarted
  await expect(dialog.locator('.profile-loading')).toBeVisible()
  await expect(dialog.locator('.user-avatar')).toHaveCount(0)
  releaseProfile()
  await expect(dialog.locator('#profile-username')).toHaveValue('pending-profile')
})

test('rejects a local preview decode failure before uploading the file', async ({ page }) => {
  await installMockApi(page)
  await page.addInitScript(() => {
    HTMLImageElement.prototype.decode = () => Promise.reject(new Error('decode failed'))
  })
  let uploadRequests = 0
  page.on('request', (request) => {
    if (request.url().includes('/api/user/avatar')) uploadRequests += 1
  })
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await dialog.locator('#profile-avatar-input').setInputFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })
  await expect(dialog.getByRole('alert')).toContainText('头像预览无法解码')
  expect(uploadRequests).toBe(0)
})

test('revokes a pending local preview when the settings panel unmounts', async ({ page }) => {
  await installMockApi(page)
  await page.addInitScript(() => {
    const revoked = [] as string[]
    Object.defineProperty(window, '__preludeRevokedAvatarUrls', {
      configurable: true,
      value: revoked,
    })
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.revokeObjectURL = (url) => {
      revoked.push(url)
      revoke(url)
    }
  })
  let releaseUpload!: () => void
  const uploadGate = new Promise<void>((resolve) => {
    releaseUpload = resolve
  })
  let uploadStartedResolve!: () => void
  const uploadStarted = new Promise<void>((resolve) => {
    uploadStartedResolve = resolve
  })
  await page.route('**/api/user/avatar', async (route) => {
    uploadStartedResolve()
    await uploadGate
    await route.fulfill({
      json: {
        code: 200,
        message: 'ok',
        data: { username: 'demo', email: 'demo@example.com', avatarUrl: '/media/avatars/late.png' },
      },
    })
  })
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await dialog.locator('#profile-avatar-input').setInputFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })
  await uploadStarted
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as Window & { __preludeRevokedAvatarUrls?: string[] }).__preludeRevokedAvatarUrls
            ?.length ?? 0,
      ),
    )
    .toBeGreaterThan(0)
  expect(
    await page.evaluate(
      () =>
        (window as Window & { __preludeRevokedAvatarUrls?: string[] })
          .__preludeRevokedAvatarUrls?.[0],
    ),
  ).toMatch(/^blob:/)
  releaseUpload()
})

test('keeps the latest avatar selection when an earlier upload responds late', async ({ page }) => {
  await installMockApi(page)
  let releaseFirstUpload!: () => void
  const firstUploadGate = new Promise<void>((resolve) => {
    releaseFirstUpload = resolve
  })
  let uploadCount = 0
  await page.route('**/api/user/avatar', async (route) => {
    uploadCount += 1
    if (uploadCount === 1) {
      await firstUploadGate
      try {
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: {
              username: 'demo',
              email: 'demo@example.com',
              avatarUrl: '/media/avatars/avatar-one.png',
            },
          },
        })
      } catch {
        // The second upload aborts this request by design.
      }
      return
    }
    await route.fulfill({
      json: {
        code: 200,
        message: 'ok',
        data: {
          username: 'demo',
          email: 'demo@example.com',
          avatarUrl: '/media/avatars/avatar-two.png',
        },
      },
    })
  })
  await page.route('**/media/avatars/avatar-two.png', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: ONE_PIXEL_PNG }),
  )
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  const avatarInput = dialog.locator('#profile-avatar-input')
  await avatarInput.setInputFiles({
    name: 'first.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })
  await expect.poll(() => uploadCount).toBe(1)
  await avatarInput.setInputFiles({
    name: 'second.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })
  await expect.poll(() => uploadCount).toBe(2)
  const avatar = dialog.locator('.user-avatar')
  await expect(avatar.locator('img')).toHaveAttribute('src', '/media/avatars/avatar-two.png')
  await expect(avatar).toHaveAttribute('data-avatar-state', 'image-loaded')
  expect(await dialog.getByRole('alert').count()).toBe(0)
  releaseFirstUpload()
})
