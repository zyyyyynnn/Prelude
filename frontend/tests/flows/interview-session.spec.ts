import { expect, test } from '@playwright/test'
import { sessionPreferencesKey } from '../../src/features/interview/model/sessionPreferences'
import { AUTH_USER_ID, installMockApi } from '../_helpers/mock-api'

const TEST_ACCOUNT_PREFERENCES_KEY = sessionPreferencesKey(`user:${AUTH_USER_ID}`)

const session = {
  sessionId: 101,
  status: 'ongoing',
  targetPosition: 'Java 后端工程师',
  currentStage: 'warmup',
}

test('migrates legacy session preferences and persists local hide behavior', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('pinnedSessionIds', '[101]')
    localStorage.setItem('deletedSessionIds', '[]')
  })
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })

  await page.goto('/interview')
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消置顶' })).toBeAttached()

  const migrated = await page.evaluate(
    (preferenceKey) => ({
      current: localStorage.getItem(preferenceKey),
      unscoped: localStorage.getItem('prelude-interview-session-preferences'),
      legacyPinned: localStorage.getItem('pinnedSessionIds'),
      legacyDeleted: localStorage.getItem('deletedSessionIds'),
    }),
    TEST_ACCOUNT_PREFERENCES_KEY,
  )
  expect(JSON.parse(migrated.current ?? '{}')).toEqual({ pinnedIds: [101], hiddenIds: [] })
  expect(migrated.unscoped).toBeNull()
  expect(migrated.legacyPinned).toBeNull()
  expect(migrated.legacyDeleted).toBeNull()

  await page.getByRole('button', { name: '删除会话' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '确定' })).toHaveCount(0)
  expect(
    await page.evaluate(
      (preferenceKey) => JSON.parse(localStorage.getItem(preferenceKey) ?? '{}'),
      TEST_ACCOUNT_PREFERENCES_KEY,
    ),
  ).toEqual({ pinnedIds: [101], hiddenIds: [] })
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toBeVisible()

  await page.getByRole('button', { name: '删除会话' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect
    .poll(() =>
      page.evaluate(
        (preferenceKey) => JSON.parse(localStorage.getItem(preferenceKey) ?? '{}'),
        TEST_ACCOUNT_PREFERENCES_KEY,
      ),
    )
    .toEqual({ pinnedIds: [101], hiddenIds: [101] })
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toHaveCount(0)
  const persisted = await page.evaluate(
    (preferenceKey) => JSON.parse(localStorage.getItem(preferenceKey) ?? '{}'),
    TEST_ACCOUNT_PREFERENCES_KEY,
  )
  expect(persisted).toEqual({ pinnedIds: [101], hiddenIds: [101] })
})

test('loads sidebar sessions after refreshing non-interview routes', async ({ page }) => {
  const finishedSession = {
    ...session,
    status: 'finished',
  }
  await installMockApi(page, { sessions: [finishedSession] })

  for (const path of ['/resumes', '/analytics']) {
    await page.goto(path)
    await expect(page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' })).toBeVisible()

    await page.reload()
    await expect(page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' })).toBeVisible()
  }
})

test('cancels stale account requests and never renders the previous account sessions', async ({
  page,
}) => {
  let releaseAccountA!: () => void
  let markAccountAStarted!: () => void
  let markAccountACompleted!: () => void
  const accountAGate = new Promise<void>((resolve) => {
    releaseAccountA = resolve
  })
  const accountAStarted = new Promise<void>((resolve) => {
    markAccountAStarted = resolve
  })
  const accountACompleted = new Promise<void>((resolve) => {
    markAccountACompleted = resolve
  })
  const requestTokens: string[] = []
  const ok = (data: unknown) => ({ code: 200, message: 'ok', data })

  await page.addInitScript(() => {
    localStorage.setItem('auth', JSON.stringify({ token: 'token-a', userId: 1 }))
  })
  await page.route(
    /\/api\/(auth|interview|user|resume|position|llm|analytics)(?:\/|$).*/,
    async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const pathname = url.pathname.replace(/^\/api/, '')
      const method = request.method()

      if (method === 'GET' && pathname === '/interview/sessions') {
        const authorization = request.headers().authorization ?? ''
        requestTokens.push(authorization)
        if (authorization === 'Bearer token-a') {
          markAccountAStarted()
          await accountAGate
          try {
            await route.fulfill({
              json: ok([
                {
                  sessionId: 1,
                  status: 'finished',
                  targetPosition: '账号 A 私有会话',
                },
              ]),
            })
          } catch {
            // The browser is expected to abort this request during account switching.
          } finally {
            markAccountACompleted()
          }
          return
        }
        return route.fulfill({
          json: ok([
            {
              sessionId: 2,
              status: 'finished',
              targetPosition: '账号 B 私有会话',
            },
          ]),
        })
      }
      if (method === 'POST' && pathname === '/auth/login') {
        return route.fulfill({ json: ok({ token: 'token-b', userId: 2 }) })
      }
      if (method === 'GET' && pathname === '/user/profile') {
        return route.fulfill({ json: ok({ username: 'account-b', email: 'b@example.com' }) })
      }
      if (method === 'GET' && pathname === '/analytics/radar') {
        return route.fulfill({
          json: ok({ technical: 0, expression: 0, logic: 0, sessionCount: 0 }),
        })
      }
      if (
        method === 'GET' &&
        (pathname === '/analytics/trend' ||
          pathname === '/analytics/weaknesses' ||
          pathname === '/resume/list' ||
          pathname === '/position/list' ||
          pathname === '/llm/providers')
      ) {
        return route.fulfill({ json: ok([]) })
      }
      if (method === 'GET' && pathname === '/user/llm-config') {
        return route.fulfill({
          json: ok({
            providerKey: 'openai-chat-completions',
            baseUrl: 'https://api.example.com/v1',
            model: 'test-model',
            hasApiKey: true,
            apiKeyMasked: 'sk-***',
          }),
        })
      }
      return route.fulfill({ json: ok(null) })
    },
  )

  await page.goto('/analytics')
  await accountAStarted

  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.getByLabel('用户名').fill('account-b')
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.locator('form').getByRole('button', { name: '登录' }).click()

  await expect
    .poll(() => requestTokens.includes('Bearer token-b'), { timeout: 10_000 })
    .toBe(true)
  const accountBSession = page.getByRole('button', {
    name: '打开已结束会话 账号 B 私有会话',
  })
  await expect(accountBSession).toBeVisible({ timeout: 10_000 })

  releaseAccountA()
  await accountACompleted

  await expect(accountBSession).toBeVisible()
  await expect(page.getByText('账号 A 私有会话')).toHaveCount(0)
  expect(requestTokens).toContain('Bearer token-a')
  expect(requestTokens).toContain('Bearer token-b')
})
