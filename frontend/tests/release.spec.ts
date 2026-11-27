import { readFile } from 'node:fs/promises'
import process from 'node:process'
import { expect, test, type Page } from '@playwright/test'

const baseURL = process.env.RELEASE_BASE_URL || 'http://127.0.0.1:14173'
const answer = '这是来自真实服务链路的测试回答。'
const firstAvatar = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4AWJiYGD4DwAAAP//cGajQwAAAAZJREFUAwABDgEC81VxbAAAAABJRU5ErkJggg==',
  'base64',
)
const secondAvatar = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
  'base64',
)

type Profile = { accountId: number; username: string; avatarUrl: string | null; revision: number }
type Position = { id: number }
type Resume = { resumeId: number }
type ResumeItem = { id: number }
type StartedSession = { sessionId: number }
type SessionItem = { sessionId: number }
type SessionDetail = {
  currentStage: string
  messages: Array<{ role: string; assessmentStatus?: string }>
}
type FinishResult = { status: string; jobId: string }
type JobView = { status: string }
type SessionView = { sessionId: string; current: boolean }

async function api<T = unknown>(
  page: Page,
  method: string,
  path: string,
  options: {
    data?: unknown
    multipart?: Record<string, { name: string; mimeType: string; buffer: Buffer }>
  } = {},
): Promise<T> {
  const cookies = await page.context().cookies()
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  const headers: Record<string, string> = { Origin: new URL(baseURL).origin }
  if (token) headers['X-XSRF-TOKEN'] = token
  if (options.data && !options.multipart) headers['Content-Type'] = 'application/json'
  const response = await page.request.fetch(`/api${path}`, {
    method,
    headers,
    data: options.data,
    multipart: options.multipart,
  })
  const text = await response.text()
  expect(response.ok(), `${method} ${path}: ${text}`).toBeTruthy()
  const payload = JSON.parse(text) as { code?: unknown; data?: unknown }
  expect(payload.code, `${method} ${path}: ${text}`).toBe(200)
  return payload.data as T
}

async function postChat(page: Page, sessionId: number, content: string, autoStart = false) {
  const cookies = await page.context().cookies()
  const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  const response = await page.request.post(
    `/api/interview/${sessionId}/chat${autoStart ? '?autoStart=true' : ''}`,
    {
      headers: {
        Origin: new URL(baseURL).origin,
        'Content-Type': 'application/json',
        ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
      },
      data: { content },
    },
  )
  const body = await response.text()
  expect(response.ok(), body).toBeTruthy()
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/interview$/)
}

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}

async function openSettings(page: Page) {
  await page.getByRole('button', { name: '设置', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '账号资料' }).click()
  return dialog
}

test('@release primary interview path works against real services', async ({ page }) => {
  const username = `release_${Date.now()}`
  const secondUsername = `${username}_other`
  const password = 'release-password'

  await page.request.get('/api/auth/me')
  await api(page, 'POST', '/auth/register', { data: { username, password } })
  await login(page, username, password)

  const profile = await api<Profile>(page, 'GET', '/user/profile')
  await api(page, 'PUT', '/user/profile', {
    data: {
      username,
      email: `${username}@example.com`,
      themePreference: 'dark',
      expectedRevision: profile.revision,
      operationId: `profile-${Date.now()}`,
    },
  })
  const withFirstAvatar = await api<Profile>(page, 'POST', '/user/avatar', {
    multipart: {
      file: { name: 'avatar.png', mimeType: 'image/png', buffer: firstAvatar },
    },
  })
  expect(withFirstAvatar.avatarUrl).toBeTruthy()

  await api(page, 'PUT', '/llm/config', {
    data: {
      provider: 'deepseek',
      model: 'deepseek-v4-pro',
      customEndpointUrl: null,
      apiKey: 'release-test',
      reasoningLevel: 'AUTO',
      maxOutputTokens: 1024,
      fallbackModels: [],
    },
  })

  const position = await api<Position>(page, 'POST', '/position', {
    data: { name: `发布 smoke 岗位 ${Date.now()}`, systemPrompt: '你是一名严谨的模拟面试官。' },
  })
  const resume = await api<Resume>(page, 'POST', '/resume/upload', {
    multipart: {
      file: {
        name: 'release-resume.pdf',
        mimeType: 'application/pdf',
        buffer: await readFile(new URL('./fixtures/resume.pdf', import.meta.url)),
      },
    },
  })
  const listedResumes = await api<ResumeItem[]>(page, 'GET', '/resume/list')
  expect(listedResumes.some((item) => item.id === resume.resumeId)).toBeTruthy()

  await page.goto('/interview')
  await selectContext(page, '选择简历', 'release-resume.pdf')

  const started = await api<StartedSession>(page, 'POST', '/interview/start', {
    data: {
      resumeId: resume.resumeId,
      positionId: position.id,
      jdText: '负责后端服务与稳定性建设',
      model: 'deepseek-v4-pro',
      reasoningLevel: 'AUTO',
      attachmentIds: [],
    },
  })
  expect(started.sessionId).toBeGreaterThan(0)
  const activity = await api<SessionItem[]>(page, 'GET', '/interview/sessions')
  expect(activity.some((item) => item.sessionId === started.sessionId)).toBeTruthy()

  await page.goto(`/interview?session=${started.sessionId}`)
  await postChat(page, started.sessionId, '', true)
  await page.reload()
  await expect(page.getByText(answer).first()).toBeVisible()

  const expectedStages = ['technical', 'deep_dive', 'deep_dive', 'closing']
  for (let turn = 1; turn <= 4; turn += 1) {
    await page
      .getByPlaceholder('输入回答…')
      .fill(`第 ${turn} 次回答：我按业务边界拆分服务，并用指标验证结果。`)
    await page.getByRole('button', { name: '发送' }).click()
    await expect(page.getByText(answer)).toHaveCount(turn + 1)
    await expect
      .poll(async () => {
        const detail = await api<SessionDetail>(
          page,
          'GET',
          `/interview/${started.sessionId}/messages`,
        )
        return detail.currentStage
      })
      .toBe(expectedStages[turn - 1])
  }
  await postChat(page, started.sessionId, '第 5 次回答：我按业务边界拆分服务，并用指标验证结果。')
  await postChat(page, started.sessionId, '第 6 次回答：我按业务边界拆分服务，并用指标验证结果。')
  await page.reload()
  await expect(page.getByText(answer)).toHaveCount(7)

  await expect
    .poll(async () => {
      const detail = await api<SessionDetail>(
        page,
        'GET',
        `/interview/${started.sessionId}/messages`,
      )
      const answers = detail.messages.filter((message) => message.role === 'user')
      return answers.at(-1)?.assessmentStatus
    })
    .toBe('SCORED')

  await expect(page.getByRole('button', { name: '生成报告' })).toBeEnabled()
  const finish = await api<FinishResult>(page, 'POST', `/interview/${started.sessionId}/finish`)
  expect(finish.status).toBe('generating')
  await expect
    .poll(async () => (await api<JobView>(page, 'GET', `/jobs/${finish.jobId}`)).status, {
      timeout: 30_000,
    })
    .toBe('SUCCEEDED')
  await page.reload()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await page.getByRole('link', { name: '数据看板' }).click()
  await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()

  let releaseAvatar!: () => void
  const avatarGate = new Promise<void>((resolve) => {
    releaseAvatar = resolve
  })
  let markAvatarFetched!: () => void
  const avatarFetched = new Promise<void>((resolve) => {
    markAvatarFetched = resolve
  })
  let delaySecondAvatar = true
  await page.route('**/api/user/avatar', async (route) => {
    if (route.request().method() === 'POST' && delaySecondAvatar) {
      delaySecondAvatar = false
      const response = await route.fetch()
      markAvatarFetched()
      await avatarGate
      await route.fulfill({ response })
      return
    }
    await route.continue()
  })

  const settings = await openSettings(page)
  const avatarRequest = page.waitForRequest('**/api/user/avatar')
  await settings.locator('#avatar-upload').setInputFiles({
    name: 'replacement-avatar.png',
    mimeType: 'image/png',
    buffer: secondAvatar,
  })
  await avatarRequest
  await avatarFetched
  await settings.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.request.get('/api/auth/me')
  await api(page, 'POST', '/auth/register', { data: { username: secondUsername, password } })
  await login(page, secondUsername, password)
  releaseAvatar()
  await page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/user/avatar') && response.request().method() === 'POST',
  )

  const secondSettings = await openSettings(page)
  await expect(secondSettings.getByLabel('用户名')).toHaveValue(secondUsername)
  expect((await api<Profile>(page, 'GET', '/user/profile')).avatarUrl).toBeNull()
  await page.keyboard.press('Escape')
  const isolatedSessions = await api<SessionItem[]>(page, 'GET', '/interview/sessions')
  expect(isolatedSessions).toEqual([])

  const sessions = await api<SessionView[]>(page, 'GET', '/auth/sessions')
  const currentSession = sessions.find((session) => session.current)
  expect(currentSession).toBeDefined()
  await api(page, 'DELETE', `/auth/sessions/${currentSession!.sessionId}`)
  expect((await page.request.get('/api/user/profile')).status()).toBe(401)
  await page.goto('/interview')
  await expect(page).toHaveURL(/\/login\?reason=expired/)
})
