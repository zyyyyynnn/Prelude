import { expect, test, type Page } from '@playwright/test'
import { createDemoState, installDemoHarness } from './demo-harness'
import { fulfillProblem } from './api-fixtures'

const validPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4AWJiYGD4DwAAAP//cGajQwAAAAZJREFUAwABDgEC81VxbAAAAABJRU5ErkJggg==',
  'base64',
)
const ok = (data: unknown) => JSON.stringify({ code: 200, message: 'ok', data })

async function openSettings(page: Page, section: string) {
  await page.getByRole('button', { name: '设置' }).click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: section }).click()
  return dialog
}

async function login(page: Page, username = 'demo') {
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page
    .locator('[data-slot="auth-form"]')
    .getByRole('button', { name: '登录', exact: true })
    .click()
  await expect(page).toHaveURL(/\/(interview|analytics)$/)
}

test('login exposes only enabled OAuth providers and keeps password login available', async ({
  page,
}) => {
  const state = createDemoState()
  state.oauthProviders = ['google']
  await installDemoHarness(page, state)
  await page.goto('/login?redirect=%2Fanalytics')

  await expect(page.locator('a').filter({ hasText: 'Google' })).toHaveAttribute(
    'href',
    /\/api\/auth\/oauth\/google\/start\?redirect=%2Fanalytics/,
  )
  await expect(page.locator('a').filter({ hasText: 'GitHub' })).toHaveCount(0)

  await login(page)
  await expect(page).toHaveURL(/\/analytics$/)
})

test('unconfigured OAuth hides provider entries and pending asks for password without an email', async ({
  page,
}) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  await page.goto('/login?oauth=pending&redirect=%2Fanalytics')

  await expect(page.locator('a').filter({ hasText: 'Google' })).toHaveCount(0)
  await expect(page.locator('a').filter({ hasText: 'GitHub' })).toHaveCount(0)
  await expect(page.getByRole('alert')).toHaveText(
    '外部账号已匹配现有账户，请使用密码登录以完成验证。',
  )
  await expect(page.locator('body')).not.toContainText('owner@example.com')
})

for (const outcome of ['server', 'network', 'malformed'] as const) {
  test(`auth bootstrap exposes retry after a ${outcome} failure`, async ({ page }) => {
    const state = createDemoState()
    state.authenticated = true
    await installDemoHarness(page, state)
    let failBootstrap = true
    await page.route('**/api/auth/me', async (route) => {
      if (!failBootstrap) {
        await route.fallback()
        return
      }
      if (outcome === 'network') {
        await route.abort('failed')
        return
      }
      if (outcome === 'malformed') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: 'not-json' })
        return
      }
      await fulfillProblem(route, 500, 'bootstrap_failed', '登录状态读取失败')
    })
    await page.goto('/interview')

    await expect(page.getByText('登录状态检查失败，请检查网络后重试。')).toBeVisible()
    await expect(page).toHaveURL(/\/interview$/)
    failBootstrap = false
    await page.getByRole('button', { name: '重新加载' }).click()
    await expect(page.getByRole('heading', { name: '准备开始一场沉浸式模拟面试' })).toBeVisible()
  })
}

test('server profile restores theme and account switch replaces account-scoped local state', async ({
  page,
}) => {
  const state = createDemoState()
  state.authenticated = true
  state.profile.themePreference = 'dark'
  await installDemoHarness(page, state)
  await page.addInitScript(() => localStorage.setItem('prelude-theme-preference', 'light'))
  await page.goto('/interview')

  await expect(page.locator('html')).toHaveClass(/\bdark\b/)
  expect(await page.evaluate(() => localStorage.getItem('prelude-theme-preference:1'))).toBe('dark')

  const dialog = await openSettings(page, '账号资料')
  await dialog.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)

  state.profile = {
    accountId: 2,
    username: 'second',
    email: 'second@example.com',
    avatarUrl: null,
    themePreference: 'light',
    revision: 4,
  }
  await page.evaluate(() => localStorage.setItem('prelude-theme-preference:2', 'dark'))
  await login(page, 'second')

  await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
  expect(await page.evaluate(() => localStorage.getItem('prelude-theme-preference:1'))).toBe('dark')
  expect(await page.evaluate(() => localStorage.getItem('prelude-theme-preference:2'))).toBe(
    'light',
  )
})

test('ProfileAvatar keeps geometry while decoding and falls back after decode failure', async ({
  page,
}) => {
  const state = createDemoState()
  state.authenticated = true
  state.profile.avatarUrl = '/api/assets/avatar-one'
  await installDemoHarness(page, state)
  await page.addInitScript(() => {
    const decoders = new Map<string, { resolve: () => void; reject: (reason: Error) => void }>()
    Object.defineProperty(window, '__avatarDecoders', { value: decoders })
    HTMLImageElement.prototype.decode = function () {
      if (this.src.endsWith('/broken')) return Promise.reject(new Error('broken image'))
      return new Promise<void>((resolve, reject) => {
        decoders.set(this.src, { resolve, reject })
      })
    }
  })
  await page.goto('/interview')
  const dialog = await openSettings(page, '账号资料')
  const placeholder = dialog.locator('span[role="img"][aria-label="当前头像"]')
  const avatar = dialog.locator('[data-slot="profile-avatar"]')
  await expect(placeholder).toBeVisible()
  const placeholderBox = await avatar.boundingBox()
  expect(placeholderBox?.width).toBe(82)
  expect(placeholderBox?.height).toBe(82)

  await expect
    .poll(() =>
      page.evaluate(() => {
        const decoders = (
          window as unknown as {
            __avatarDecoders: Map<string, { resolve: () => void }>
          }
        ).__avatarDecoders
        return decoders.has(new URL('/api/assets/avatar-one', location.origin).href)
      }),
    )
    .toBe(true)
  await page.evaluate(() => {
    const decoders = (
      window as unknown as { __avatarDecoders: Map<string, { resolve: () => void }> }
    ).__avatarDecoders
    decoders.get(new URL('/api/assets/avatar-one', location.origin).href)?.resolve()
  })

  const image = dialog.locator('img[alt="当前头像"]')
  await expect(image).toBeVisible()
  await expect(placeholder).toHaveCount(0)
  const imageBox = await image.boundingBox()
  expect(imageBox?.width).toBe(placeholderBox?.width)
  expect(imageBox?.height).toBe(placeholderBox?.height)

  state.profile.avatarUrl = '/api/assets/broken'
  await page.reload()
  const reopened = await openSettings(page, '账号资料')
  await expect(reopened.locator('span[role="img"][aria-label="当前头像"]')).toBeVisible()
  await expect(reopened.locator('img[alt="当前头像"]')).toHaveCount(0)
})

test('profile save and avatar upload are mutually exclusive', async ({ page }) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  let releaseUpload!: () => void
  const uploadGate = new Promise<void>((resolve) => {
    releaseUpload = resolve
  })
  await page.route('**/api/user/avatar', async (route) => {
    await uploadGate
    await route.fallback()
  })
  await page.goto('/login')
  await login(page)
  const dialog = await openSettings(page, '账号资料')

  const upload = page.waitForRequest('**/api/user/avatar')
  await dialog.locator('#avatar-upload').setInputFiles({
    name: '头像.png',
    mimeType: 'image/png',
    buffer: validPng,
  })
  await upload

  await expect(dialog.getByRole('button', { name: '上传头像' })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: '保存设置' })).toBeDisabled()
  await dialog.locator('#profile-settings-form').evaluate((form) => {
    if (form instanceof HTMLFormElement) form.requestSubmit()
  })
  expect(state.requests.filter((request) => request.method === 'PUT')).toHaveLength(0)

  releaseUpload()
  await expect(dialog.getByRole('button', { name: '保存设置' })).toBeEnabled()
})

test('a 409 reloads the authoritative revision before the next save', async ({ page }) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  const revisions: number[] = []
  await page.route('**/api/user/profile', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback()
      return
    }
    const payload = route.request().postDataJSON() as {
      username: string
      expectedRevision: number
    }
    revisions.push(payload.expectedRevision)
    if (revisions.length === 1) {
      state.profile = { ...state.profile, username: 'authoritative', revision: 1 }
      await route.fulfill({
        status: 409,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'about:blank',
          title: 'Conflict',
          status: 409,
          detail: '资料已被其他操作更新，请刷新后重试',
          code: 'revision_conflict',
        }),
      })
      return
    }
    state.profile = { ...state.profile, username: payload.username, revision: 2 }
    await route.fulfill({ status: 200, contentType: 'application/json', body: ok(state.profile) })
  })
  await page.goto('/login')
  await login(page)
  const dialog = await openSettings(page, '账号资料')

  await dialog.getByLabel('用户名').fill('stale-name')
  await dialog.getByRole('button', { name: '保存设置' }).click()
  await expect(dialog.getByLabel('用户名')).toHaveValue('authoritative')
  await expect(dialog.getByRole('button', { name: '保存设置' })).toBeEnabled()

  await dialog.getByLabel('用户名').fill('fresh-name')
  await dialog.getByRole('button', { name: '保存设置' }).click()
  await expect(dialog.getByLabel('用户名')).toHaveValue('fresh-name')
  expect(revisions).toEqual([0, 1])
})

test('an old account upload cannot overwrite the next account profile', async ({ page }) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  const previousProfile = { ...state.profile, username: 'first' }
  let releaseUpload!: () => void
  const uploadGate = new Promise<void>((resolve) => {
    releaseUpload = resolve
  })
  await page.route('**/api/user/avatar', async (route) => {
    await uploadGate
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({ ...previousProfile, avatarUrl: '/api/assets/old-avatar', revision: 9 }),
    })
  })
  await page.goto('/login')
  await login(page, 'first')
  const dialog = await openSettings(page, '账号资料')
  const upload = page.waitForRequest('**/api/user/avatar')
  await dialog.locator('#avatar-upload').setInputFiles({
    name: '头像.png',
    mimeType: 'image/png',
    buffer: validPng,
  })
  await upload

  await dialog.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  state.profile = {
    accountId: 2,
    username: 'second',
    email: 'second@example.com',
    avatarUrl: null,
    themePreference: 'light',
    revision: 4,
  }
  await login(page, 'second')

  const oldResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/user/avatar') && response.request().method() === 'POST',
  )
  releaseUpload()
  await oldResponse

  const nextDialog = await openSettings(page, '账号资料')
  await expect(nextDialog.getByLabel('用户名')).toHaveValue('second')
  await expect(nextDialog.getByRole('button', { name: '保存设置' })).toBeEnabled()
})

for (const outcome of ['success', 'error'] as const) {
  test(`a late theme save ${outcome} cannot affect the next account`, async ({ page }) => {
    const state = createDemoState()
    state.profile.themePreference = 'dark'
    const previousProfile = { ...state.profile }
    await installDemoHarness(page, state)

    let releaseSave!: () => void
    const saveGate = new Promise<void>((resolve) => {
      releaseSave = resolve
    })
    let markSaveStarted!: () => void
    const saveStarted = new Promise<void>((resolve) => {
      markSaveStarted = resolve
    })
    await page.route('**/api/user/profile', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback()
        return
      }
      markSaveStarted()
      await saveGate
      if (outcome === 'error') {
        await route.fulfill({
          status: 500,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'about:blank',
            title: 'Internal Server Error',
            status: 500,
            detail: '主题保存失败',
            code: 'internal_error',
          }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: ok({ ...previousProfile, themePreference: 'dark', revision: 9 }),
      })
    })

    await page.goto('/login')
    await login(page)
    const dialog = await openSettings(page, '主题')
    await dialog.getByRole('radio', { name: /浅色/ }).click()
    await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
    await dialog.getByRole('button', { name: '保存主题' }).click()
    await saveStarted

    await dialog.getByRole('button', { name: '退出登录' }).click()
    await expect(page).toHaveURL(/\/login$/)
    state.profile = {
      accountId: 2,
      username: 'second',
      email: 'second@example.com',
      avatarUrl: null,
      themePreference: 'light',
      revision: 4,
    }
    await login(page, 'second')
    const nextDialog = await openSettings(page, '主题')
    await expect(nextDialog.getByRole('radio', { name: /浅色/ })).toBeChecked()

    const lateResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/user/profile') && response.request().method() === 'PUT',
    )
    releaseSave()
    await lateResponse
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveCount(0)

    const reopened = await openSettings(page, '主题')
    await expect(reopened.getByRole('radio', { name: /浅色/ })).toBeChecked()
    await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
    expect(await page.evaluate(() => localStorage.getItem('prelude-theme-preference:1'))).toBe(
      'light',
    )
    expect(await page.evaluate(() => localStorage.getItem('prelude-theme-preference:2'))).toBe(
      'light',
    )
  })
}
