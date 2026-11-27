import { expect, test, type Page, type Route } from '@playwright/test'
import { installAnonymousSession } from './auth-bootstrap'
import {
  createProviderFixtures,
  customModelCapability,
  fulfillJson,
  fulfillProblem,
  fulfillUnknownEndpoint,
  jsonBody,
  modelCapability,
} from './api-fixtures'

type ApiState = {
  sessions?: unknown[]
  session?: Record<string, unknown>
  requests: Array<{ path: string; method: string; body: unknown }>
}

const deepSeekCapability = (model = 'deepseek-v4-pro') => modelCapability('deepseek', model)
const providers = createProviderFixtures()

async function installApi(page: Page, state: ApiState) {
  await page.route(/^https?:\/\/[^/]+\/api\//, async (route) => respond(route, state))
}

async function installVoiceHarness(page: Page, delayMedia = false) {
  await page.addInitScript(
    ({ delayed }) => {
      const voiceState = { closed: 0, recorderStarts: 0, stoppedTracks: 0 }
      class MockWebSocket {
        static OPEN = 1
        readyState = MockWebSocket.OPEN
        binaryType = ''
        onopen: ((event: Event) => void) | null = null
        onmessage: ((event: MessageEvent) => void) | null = null
        onerror: ((event: Event) => void) | null = null
        onclose: ((event: CloseEvent) => void) | null = null
        constructor() {
          ;(window as unknown as { voiceSocket: MockWebSocket }).voiceSocket = this
          setTimeout(() => this.onopen?.(new Event('open')), 0)
        }
        send() {}
        close() {
          voiceState.closed += 1
          this.readyState = 3
          this.onclose?.(new CloseEvent('close'))
        }
      }
      class MockMediaRecorder {
        state = 'inactive'
        ondataavailable: ((event: BlobEvent) => void) | null = null
        onstop: (() => void) | null = null
        start() {
          voiceState.recorderStarts += 1
          this.state = 'recording'
        }
        stop() {
          this.state = 'inactive'
          this.onstop?.()
        }
      }
      const media = {
        getTracks: () => [{ stop: () => (voiceState.stoppedTracks += 1) }],
      } as unknown as MediaStream
      let releaseMedia: (() => void) | undefined
      Object.defineProperty(window, 'WebSocket', { configurable: true, value: MockWebSocket })
      Object.defineProperty(window, 'MediaRecorder', {
        configurable: true,
        value: MockMediaRecorder,
      })
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: {
          getUserMedia: () =>
            delayed
              ? new Promise<MediaStream>((resolve) => {
                  releaseMedia = () => resolve(media)
                })
              : Promise.resolve(media),
        },
      })
      ;(window as unknown as { voiceState: typeof voiceState }).voiceState = voiceState
      ;(window as unknown as { releaseMedia: () => void }).releaseMedia = () => releaseMedia?.()
    },
    { delayed: delayMedia },
  )
}

async function respond(route: Route, state: ApiState) {
  const request = route.request()
  const path = new URL(request.url()).pathname
  const method = request.method()
  const rawBody = request.postData()
  const contentType = request.headers()['content-type'] ?? ''
  const body: unknown =
    rawBody && contentType.includes('application/json') ? (JSON.parse(rawBody) as unknown) : null
  state.requests.push({ path, method, body })
  if (/\/api\/interview\/\d+\/chat$/.test(path)) {
    const message = {
      id: 4,
      role: 'assistant',
      content: '下面继续讨论系统边界。',
    }
    const existing = Array.isArray(state.session?.messages)
      ? (state.session.messages as unknown[])
      : []
    state.session = {
      ...state.session,
      messages: [
        ...existing,
        { id: 3, role: 'user', content: (body as { content: string }).content },
        message,
      ],
    }
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: `event: message\ndata: ${message.content}\n\n`,
    })
    return
  }
  let handled = false
  const deleted = /^\/api\/interview\/(\d+)$/.exec(path)
  if (deleted && method === 'DELETE') {
    const id = Number(deleted[1])
    state.sessions = (state.sessions ?? []).filter(
      (item) => (item as { sessionId: number }).sessionId !== id,
    )
    handled = true
  } else if (/\/api\/interview\/\d+\/pin$/.test(path) && method === 'PATCH') {
    const id = Number(/\/api\/interview\/(\d+)\/pin$/.exec(path)?.[1])
    const pinned = Boolean((body as { pinned?: boolean }).pinned)
    state.sessions = (state.sessions ?? []).map((item) => {
      const session = item as { sessionId: number }
      return session.sessionId === id ? { ...session, pinned } : item
    })
    handled = true
  } else if (path === '/api/auth/logout' && method === 'POST') {
    handled = true
  }
  let data: unknown = null
  if (path === '/api/auth/me') data = { accountId: 1, username: 'prelude' }
  else if (path === '/api/auth/oauth/providers') data = []
  else if (path === '/api/interview/sessions') data = state.sessions ?? []
  else if (/\/api\/interview\/\d+\/messages$/.test(path)) data = state.session
  else if (path === '/api/position/list')
    data = [
      { id: 1, name: 'Java 后端工程师' },
      { id: 2, name: '前端工程师' },
    ]
  else if (path === '/api/resume/list')
    data = [
      { id: 1, fileName: '候选人简历.pdf', sessionCount: 2, inUse: false },
      { id: 2, fileName: '作品集简历.pdf', sessionCount: 0, inUse: false },
    ]
  else if (path === '/api/resume/upload' && method === 'POST')
    data = { id: 3, fileName: 'intent-resume.pdf' }
  else if (path === '/api/user/profile')
    data = {
      accountId: 1,
      username: 'prelude',
      email: 'prelude@example.com',
      themePreference: 'system',
      revision: 0,
    }
  else if (path === '/api/llm/providers') data = providers
  else if (path === '/api/llm/config/discover-models' && method === 'POST')
    data = {
      baseUrl: (body as { baseUrl?: string }).baseUrl ?? '',
      models: [
        customModelCapability(
          (body as { provider?: string }).provider ?? 'openai-chat-completions',
          'account-discovered-model',
        ),
      ],
    }
  else if (path === '/api/llm/config/discover-capabilities' && method === 'POST')
    data = customModelCapability(
      (body as { provider?: string }).provider ?? 'openai-chat-completions',
      (body as { model?: string }).model ?? 'account-discovered-model',
      ['AUTO', 'LOW', 'MEDIUM', 'HIGH', 'XHIGH', 'MAX'],
    )
  else if (path === '/api/attachments' && method === 'POST')
    data = {
      id: 51,
      fileName: 'architecture.md',
      mediaType: 'text/markdown',
      size: 24,
      image: false,
    }
  else if (path === '/api/llm/config' && method === 'GET')
    data = {
      provider: 'deepseek',
      model: 'deepseek-v4-pro',
      customEndpointUrl: null,
      hasApiKey: false,
      apiKeyMasked: null,
      reasoningLevel: 'AUTO',
      maxOutputTokens: 4096,
      fallbackModels: [],
      capability: deepSeekCapability(),
    }
  else if (path === '/api/interview/start')
    data = {
      sessionId: 11,
      targetPosition: '前端工程师',
      currentStage: 'warmup',
    }
  else if (path === '/api/llm/config' && method === 'PUT')
    data = {
      provider: (body as { provider?: string }).provider ?? 'deepseek',
      model: (body as { model?: string }).model ?? 'deepseek-v4-pro',
      customEndpointUrl: (body as { customEndpointUrl?: string }).customEndpointUrl ?? null,
      hasApiKey: true,
      apiKeyMasked: 'sk-***',
      reasoningLevel: (body as { reasoningLevel?: string }).reasoningLevel ?? 'AUTO',
      maxOutputTokens: (body as { maxOutputTokens?: number }).maxOutputTokens ?? 4096,
      fallbackModels: [],
      capability:
        ((body as { provider?: string }).provider ?? 'deepseek') === 'deepseek'
          ? deepSeekCapability((body as { model?: string }).model ?? 'deepseek-v4-pro')
          : customModelCapability(
              (body as { provider?: string }).provider ?? 'openai-chat-completions',
              (body as { model?: string }).model ?? 'account-discovered-model',
              (body as { reasoningLevel?: string }).reasoningLevel === 'HIGH'
                ? ['AUTO', 'HIGH']
                : ['AUTO'],
            ),
    }
  if (!handled && data === null) return fulfillUnknownEndpoint(route)
  await fulfillJson(route, data)
}

test('@smoke sends the selected prompt bar context when starting an interview', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')

  await page.locator('#interview-attachment-upload').setInputFiles({
    name: 'architecture.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# Architecture'),
  })
  await expect(page.getByText('architecture.md')).toBeVisible()
  const removeAttachment = page.getByRole('button', { name: '移除附件：architecture.md' })
  const removeGeometry = await removeAttachment.evaluate((button) => {
    const control = button.getBoundingClientRect()
    const icon = button.querySelector('svg')!.getBoundingClientRect()
    return {
      control: { width: control.width, height: control.height },
      icon: { width: icon.width, height: icon.height },
      centered:
        Math.abs(control.left + control.width / 2 - (icon.left + icon.width / 2)) < 1 &&
        Math.abs(control.top + control.height / 2 - (icon.top + icon.height / 2)) < 1,
    }
  })
  expect(removeGeometry.control.width).toBeGreaterThan(0)
  expect(removeGeometry.control.height).toBeGreaterThan(0)
  expect(removeGeometry.icon.width).toBeGreaterThan(0)
  expect(removeGeometry.icon.height).toBeGreaterThan(0)
  expect(removeGeometry.centered).toBe(true)
  await selectContext(page, '选择简历', '作品集简历.pdf')
  await selectContext(page, '选择岗位', '前端工程师')
  await page.getByLabel('职位描述（可选）').fill('负责复杂交互与前端架构。')
  await page.getByRole('button', { name: '开始面试' }).click()

  await expect(page).toHaveURL(/session=11/)
  const request = state.requests.find((item) => item.path === '/api/interview/start')
  expect(request?.body).toEqual({
    resumeId: 2,
    positionId: 2,
    jdText: '负责复杂交互与前端架构。',
    model: 'deepseek-v4-pro',
    reasoningLevel: 'AUTO',
    attachmentIds: [51],
  })
})

test('@smoke presents request failures as a dismissible top system toast', async ({ page }) => {
  await installAnonymousSession(page)
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'service_unavailable',
        status: 503,
        detail: '服务暂不可用',
        code: 'service_unavailable',
      }),
    })
  })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.locator('#auth-password').fill('password')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '服务暂不可用' })
  await toast.evaluate((element) => {
    const closeButton = element.querySelector<HTMLButtonElement>(
      'button[aria-label="关闭系统提示"]',
    )
    if (!closeButton) throw new Error('系统提示缺少可访问的关闭按钮')
    closeButton.click()
  })
  await expect(toast).toHaveCount(0)
  await expect(page.locator('[data-slot="auth-form"]').getByText('服务暂不可用')).toHaveCount(0)
  await expect(page).toHaveURL(/\/login$/)
})

test('@smoke submits registration and returns to the login form', async ({ page }) => {
  await installAnonymousSession(page)
  const submits: Array<{ path: string; method: string; body: unknown }> = []
  await page.route('**/api/auth/register', async (route) => {
    submits.push({
      path: new URL(route.request().url()).pathname,
      method: route.request().method(),
      body: route.request().postDataJSON(),
    })
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: null }),
    })
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await page.getByLabel('用户名').fill('new-candidate')
  await page.locator('#auth-password').fill('correct-horse')
  await page.getByLabel('邮箱').fill('candidate@example.com')
  await page.getByRole('button', { name: '完成注册' }).click()

  await expect(
    page.locator('[data-sonner-toast]').filter({ hasText: '注册成功，请继续登录。' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true }).last()).toBeVisible()
  expect(submits).toHaveLength(1)
  expect(submits[0].body).toEqual({
    username: 'new-candidate',
    password: 'correct-horse',
    email: 'candidate@example.com',
  })
})

test('@smoke keeps authentication validation out of the form layout', async ({ page }) => {
  await installAnonymousSession(page)
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '密码至少需要 6 个字符' })
  await expect(toast).toBeAttached()
  await expect(page.locator('#auth-password')).toBeFocused()
  await expect(
    page.locator('[data-slot="auth-form"]').getByText('密码至少需要 6 个字符'),
  ).toHaveCount(0)
})

test('@smoke routes prompt bar management actions into global settings', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')

  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: /选择简历/ }).hover()
  const chooser = page.waitForEvent('filechooser')
  await page.getByRole('menuitem', { name: '新建简历' }).click()
  const fileChooser = await chooser
  await fileChooser.setFiles({
    name: 'intent-resume.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n% intent fixture\n'),
  })
  await expect(page.getByText('简历已上传并完成解析')).toBeVisible()
  expect(
    state.requests.filter((item) => item.method === 'POST' && item.path === '/api/resume/upload'),
  ).toHaveLength(1)
  await expect(page.getByRole('heading', { name: '简历管理' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeHidden()

  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: /选择岗位/ }).hover()
  await page.getByRole('menuitem', { name: '新建岗位' }).click()
  await expect(page.getByRole('heading', { name: '岗位管理' })).toBeVisible()
  await expect(page.getByLabel('岗位名称')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeHidden()

  await page.getByRole('button', { name: /模型：deepseek-v4-pro · 默认/ }).click()
  await page.getByRole('menuitem', { name: '管理模型' }).click()
  await expect(page.getByRole('heading', { name: '模型管理' })).toBeVisible()
  await expect(page.getByLabel('接入方式')).toContainText('DeepSeek')
  await expect(page.getByText(/个可用模型/)).toHaveCount(0)
  await expect(page.getByText('尚未保存 API Key')).toHaveCount(0)
  await expect(page.getByRole('status')).toHaveCount(0)
})

test('@smoke centers the async button indicator without resizing the control', async ({ page }) => {
  const state: ApiState = { requests: [] }
  let releaseSave!: () => void
  const saveGate = new Promise<void>((resolve) => {
    releaseSave = resolve
  })
  await installApi(page, state)
  await page.route('**/api/llm/config', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    await saveGate
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody({
        provider: (body as { provider?: string }).provider ?? 'deepseek',
        model: (body as { model?: string }).model ?? 'deepseek-v4-pro',
        customEndpointUrl: null,
        hasApiKey: false,
        apiKeyMasked: null,
        reasoningLevel: (body as { reasoningLevel?: string }).reasoningLevel ?? 'AUTO',
        maxOutputTokens: (body as { maxOutputTokens?: number }).maxOutputTokens ?? 4096,
        fallbackModels: [],
        capability: deepSeekCapability((body as { model?: string }).model ?? 'deepseek-v4-pro'),
      }),
    })
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()

  const save = page.getByRole('button', { name: '保存设置' })
  await expect(save).toBeVisible()
  const idleWidth = (await save.boundingBox())!.width
  await save.click()
  await expect(save).toHaveAttribute('aria-busy', 'true')
  const loading = await save.evaluate((button) => {
    const control = button.getBoundingClientRect()
    const spinner = button
      .querySelector<HTMLElement>('.ui-button__spinner')!
      .getBoundingClientRect()
    const content = button.querySelector<HTMLElement>('.ui-button__content')!
    return {
      width: control.width,
      centered:
        Math.abs(control.left + control.width / 2 - (spinner.left + spinner.width / 2)) < 1 &&
        Math.abs(control.top + control.height / 2 - (spinner.top + spinner.height / 2)) < 1,
      contentOpacity: getComputedStyle(content).opacity,
    }
  })
  expect(loading).toEqual({ width: idleWidth, centered: true, contentOpacity: '0' })
  releaseSave()

  await expect(page.getByText('LLM 配置已保存')).toBeVisible()
  await expect(save).not.toHaveAttribute('aria-busy')
  await expect(save.locator('.ui-button__content')).toHaveCSS('opacity', '1')
  expect((await save.boundingBox())!.width).toBe(idleWidth)
})

test('@smoke keeps prompt model selection local and sends the current draft', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')
  await selectContext(page, '选择简历', '作品集简历.pdf')
  await selectContext(page, '选择岗位', '前端工程师')

  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /^模型\s/ }).hover()
  await page.getByRole('menuitemradio', { name: 'deepseek-v4-flash', exact: true }).click()
  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '高', exact: true }).click()

  const modelTrigger = page.getByRole('button', { name: /模型：/ })
  await expect(modelTrigger).toHaveText(/deepseek-v4-flash · 高/)
  await expect(page.getByText('模型配置已更新')).toHaveCount(0)
  expect(
    state.requests.some(
      (request) => request.path === '/api/llm/config' && request.method === 'PUT',
    ),
  ).toBe(false)

  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page).toHaveURL(/session=11/)
  const request = state.requests.find((item) => item.path === '/api/interview/start')
  expect(request?.body).toEqual({
    resumeId: 2,
    positionId: 2,
    model: 'deepseek-v4-flash',
    reasoningLevel: 'HIGH',
  })
  expect(
    state.requests.some((item) => item.path === '/api/llm/config' && item.method === 'PUT'),
  ).toBe(false)
  await expect(page.getByText('模型配置已更新')).toHaveCount(0)
})

test('@smoke blocks incompatible model drafts and preserves selections after start failure', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  const limitedModel = 'deepseek-v4-limited'
  await installApi(page, state)
  await page.route('**/api/llm/providers', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody(
        providers.map((provider) =>
          provider.providerKey === 'deepseek'
            ? {
                ...provider,
                models: [
                  ...provider.models,
                  {
                    ...deepSeekCapability(limitedModel),
                    supportedReasoningLevels: ['AUTO', 'LOW'],
                  },
                ],
              }
            : provider,
        ),
      ),
    })
  })
  await page.goto('/interview')
  await selectContext(page, '选择简历', '作品集简历.pdf')
  await selectContext(page, '选择岗位', '前端工程师')

  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '高', exact: true }).click()
  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /^模型\s/ }).hover()
  await page.getByRole('menuitemradio', { name: limitedModel, exact: true }).click()

  const start = page.getByRole('button', { name: '开始面试' })
  await expect(page.getByRole('alert')).toHaveText('该模型不支持当前思考深度，请选择兼容的思考深度')
  await expect(start).toBeDisabled()
  expect(state.requests.some((request) => request.path === '/api/interview/start')).toBe(false)
  expect(
    state.requests.some(
      (request) => request.path === '/api/llm/config' && request.method === 'PUT',
    ),
  ).toBe(false)

  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '低', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(start).toBeEnabled()
  await expect(page.getByRole('button', { name: /模型：/ })).toHaveText(
    new RegExp(`${limitedModel} · 低`),
  )

  await page.route('**/api/interview/start', async (route) => {
    state.requests.push({
      path: '/api/interview/start',
      method: 'POST',
      body: route.request().postDataJSON(),
    })
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'service_unavailable',
        status: 503,
        detail: '模型服务暂不可用',
        code: 'service_unavailable',
      }),
    })
  })
  await start.click()
  await expect(
    page.locator('[data-sonner-toast]').filter({ hasText: '模型服务暂不可用' }),
  ).toBeVisible()
  await expect(page).toHaveURL(/\/interview$/)
  await expect(page.getByRole('button', { name: /模型：/ })).toHaveText(
    new RegExp(`${limitedModel} · 低`),
  )
  const request = state.requests.find((item) => item.path === '/api/interview/start')
  expect(request?.body).toMatchObject({ model: limitedModel, reasoningLevel: 'LOW' })
  expect(
    state.requests.some((item) => item.path === '/api/llm/config' && item.method === 'PUT'),
  ).toBe(false)
})

test('@smoke pins and deletes sessions through the session API', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    sessions: [
      {
        sessionId: 11,
        targetPosition: '平台工程师',
        status: 'ongoing',
        currentStage: 'technical',
        createdAt: '2026-08-28T08:00:00Z',
      },
      {
        sessionId: 12,
        targetPosition: '前端工程师',
        status: 'finished',
        createdAt: '2026-08-27T08:00:00Z',
      },
    ],
  }
  await installApi(page, state)
  await page.goto('/interview')
  await page.getByRole('button', { name: '置顶会话' }).first().click()
  await expect(page.getByRole('button', { name: '取消置顶' }).first()).toBeVisible()
  expect(
    state.requests.some(
      ({ method, path: requestPath }) =>
        method === 'PATCH' && requestPath === '/api/interview/11/pin',
    ),
  ).toBe(true)

  await page.getByRole('button', { name: '删除会话' }).first().click()
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByText('平台工程师')).toHaveCount(0)
  expect(
    state.requests.some(
      ({ method, path: requestPath }) => method === 'DELETE' && requestPath === '/api/interview/11',
    ),
  ).toBe(true)
})

test('@smoke animates the RoseThree loading card and follows reduced-motion changes', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await installApi(page, { requests: [] })
  await page.goto('/components-lab')

  const rose = page.locator('[data-slot="rose-three"]')
  await expect(rose).toHaveAttribute('transform', /rotate\(/)
  const firstFrame = await rose.getAttribute('transform')
  await expect.poll(() => rose.getAttribute('transform')).not.toBe(firstFrame)

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect.poll(() => rose.getAttribute('transform')).toBe('rotate(0.0 50 50)')
  await page.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
  )
  await expect(rose).toHaveAttribute('transform', 'rotate(0.0 50 50)')

  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await expect.poll(() => rose.getAttribute('transform')).not.toBe('rotate(0.0 50 50)')
})

test('@smoke streams an interview answer with bounded context', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请描述你的服务拆分原则。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')
  await page.getByPlaceholder('输入回答…').fill('先按业务能力划分边界。')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('下面继续讨论系统边界。')).toBeVisible()
  const chat = state.requests.find((request) => request.path.endsWith('/chat'))
  expect(chat?.body).toMatchObject({
    content: '先按业务能力划分边界。',
    messages: [
      { role: 'assistant', content: '请描述你的服务拆分原则。' },
      { role: 'user', content: '先按业务能力划分边界。' },
    ],
  })
})

test('@smoke keeps report generation disabled before the closing stage', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请描述你的服务拆分原则。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')

  await expect(page.getByRole('button', { name: '生成报告' })).toBeVisible()
  await expect(page.getByRole('button', { name: '生成报告' })).toBeDisabled()
  await expect(page.getByText('结束面试')).toHaveCount(0)
  await expect(page.getByText('破冰')).toHaveCount(0)
  await expect(page.getByText('深挖追问')).toHaveCount(0)
  await expect(page.getByText('收尾')).toHaveCount(0)
})

test('@smoke enables report generation only during the closing stage', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'closing',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '面试即将结束。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')

  await expect(page.getByRole('button', { name: '生成报告' })).toBeEnabled()
})

test('@smoke waits for closing answer assessment before generating a report', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'closing',
      summaryReport: null,
      stages: [],
      messages: [
        { id: 1, role: 'assistant', content: '面试即将结束。' },
        {
          id: 2,
          role: 'user',
          content: '这是我的收尾回答。',
          assessmentStatus: 'PENDING',
        },
      ],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')

  await expect(page.getByRole('button', { name: '回答评估中' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '生成报告' })).toHaveCount(0)
})

test('@smoke keeps the active session when a requested session fails and retries that target', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    sessions: [
      { sessionId: 11, targetPosition: '会话 A', status: 'ongoing' },
      { sessionId: 12, targetPosition: '会话 B', status: 'ongoing' },
    ],
  }
  await installApi(page, state)
  let bAttempts = 0
  await page.route('**/api/interview/*/messages', async (route) => {
    const sessionId = Number(new URL(route.request().url()).pathname.split('/').at(-2))
    if (sessionId === 12 && bAttempts++ < 2) {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          type: 'about:blank',
          title: 'service_unavailable',
          status: 503,
          detail: '会话 B 暂时不可用',
          code: 'service_unavailable',
        }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody({
        sessionId,
        targetPosition: sessionId === 11 ? '会话 A' : '会话 B',
        status: 'ongoing',
        currentStage: 'technical',
        summaryReport: null,
        stages: [],
        messages: [
          {
            id: sessionId,
            role: 'assistant',
            content: sessionId === 11 ? 'A 正在显示' : 'B 已加载',
          },
        ],
        resumeId: 1,
        positionId: 1,
        attachments: [],
      }),
    })
  })

  await page.goto('/interview?session=11')
  await expect(page.getByText('A 正在显示')).toBeVisible()
  await page.getByRole('button', { name: '打开会话 会话 B' }).click()

  await expect(page).toHaveURL(/session=11/)
  await expect(page.getByText('A 正在显示')).toBeVisible()
  await expect(page.getByText('会话 B 暂时不可用')).toBeVisible()
  await page.getByRole('button', { name: '重试打开会话 会话 B' }).click()
  await expect(page).toHaveURL(/session=12/)
  await expect(page.getByText('B 已加载')).toBeVisible()
})

test('@smoke prevents a late session request from overwriting the latest target', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    sessions: [
      { sessionId: 11, targetPosition: '会话 A', status: 'ongoing' },
      { sessionId: 12, targetPosition: '会话 B', status: 'ongoing' },
      { sessionId: 13, targetPosition: '会话 C', status: 'ongoing' },
    ],
  }
  await installApi(page, state)
  let releaseB!: () => void
  let finishB!: () => void
  const bGate = new Promise<void>((resolve) => {
    releaseB = resolve
  })
  const bFinished = new Promise<void>((resolve) => {
    finishB = resolve
  })
  await page.route('**/api/interview/*/messages', async (route) => {
    const sessionId = Number(new URL(route.request().url()).pathname.split('/').at(-2))
    try {
      if (sessionId === 12) await bGate
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: jsonBody({
          sessionId,
          targetPosition: `会话 ${String.fromCharCode(54 + sessionId)}`,
          status: 'ongoing',
          currentStage: 'technical',
          summaryReport: null,
          stages: [],
          messages: [{ id: sessionId, role: 'assistant', content: `会话 ${sessionId} 已加载` }],
          resumeId: 1,
          positionId: 1,
          attachments: [],
        }),
      })
    } finally {
      if (sessionId === 12) finishB()
    }
  })

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '打开会话 会话 B' }).click()
  await page.getByRole('button', { name: '打开会话 会话 C' }).click()
  await expect(page).toHaveURL(/session=13/)
  releaseB()
  await bFinished
  await expect(page).toHaveURL(/session=13/)
  await expect(page.getByText('会话 13 已加载')).toBeVisible()
})

test('@smoke auto-starts a prefetched empty session once under StrictMode', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    sessions: [
      { sessionId: 11, targetPosition: '会话 A', status: 'ongoing' },
      { sessionId: 12, targetPosition: '会话 B', status: 'ongoing' },
    ],
  }
  await installApi(page, state)
  await page.route('**/api/interview/*/messages', async (route) => {
    const sessionId = Number(new URL(route.request().url()).pathname.split('/').at(-2))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody({
        sessionId,
        targetPosition: sessionId === 11 ? '会话 A' : '会话 B',
        status: 'ongoing',
        currentStage: 'technical',
        summaryReport: null,
        stages: [],
        messages: sessionId === 11 ? [{ id: 11, role: 'assistant', content: 'A 正在显示' }] : [],
        resumeId: 1,
        positionId: 1,
        attachments: [],
      }),
    })
  })

  await page.goto('/interview?session=11')
  const autoStartResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.endsWith('/api/interview/12/chat') &&
      response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '打开会话 会话 B' }).click()
  await expect(page).toHaveURL(/session=12/)
  await autoStartResponse
  await expect(page.getByText('下面继续讨论系统边界。')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送' })).toBeDisabled()
  await page.getByPlaceholder('输入回答…').fill('继续回答')
  await expect(page.getByRole('button', { name: '发送' })).toBeEnabled()
  const autoStartRequests = () =>
    state.requests.filter(
      (request) => request.path === '/api/interview/12/chat' && request.method === 'POST',
    )
  expect(autoStartRequests()).toHaveLength(1)
})

test('@smoke restores the authoritative session after stream failure under StrictMode', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '服务端权威消息' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  let releaseFailure!: () => void
  const failureGate = new Promise<void>((resolve) => {
    releaseFailure = resolve
  })
  await page.route('**/api/interview/11/chat', async (route) => {
    await failureGate
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'service_unavailable',
        status: 503,
        detail: '流式服务暂不可用',
        code: 'service_unavailable',
      }),
    })
  })

  await page.goto('/interview?session=11')
  await page.getByPlaceholder('输入回答…').fill('这条乐观消息必须回滚')
  const failureResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/interview/11/chat'),
  )
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('这条乐观消息必须回滚')).toBeVisible()
  releaseFailure()
  await failureResponse
  await expect(page.getByText('流式服务暂不可用')).toBeVisible()
  await expect(page.getByText('这条乐观消息必须回滚')).toHaveCount(0)
  await expect(page.getByText('服务端权威消息')).toBeVisible()
})

test('@smoke expires authentication when the SSE handshake returns 401', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请回答问题。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.route('**/api/interview/11/chat', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'authentication_required',
        status: 401,
        detail: '登录已失效',
        code: 'authentication_required',
      }),
    })
  })

  await page.goto('/interview?session=11')
  await page.getByPlaceholder('输入回答…').fill('触发认证失效')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page).toHaveURL(/\/login\?reason=expired/)
  await expect(page.getByText('登录已失效，请重新登录。')).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('prelude-user-id'))).toBeNull()
})

test('@smoke isolates account-scoped queries when a previous principal completes late', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  let profileRequests = 0
  let releaseAccountA!: () => void
  let finishAccountA!: () => void
  const accountAGate = new Promise<void>((resolve) => {
    releaseAccountA = resolve
  })
  const accountAFinished = new Promise<void>((resolve) => {
    finishAccountA = resolve
  })
  await page.route('**/api/user/profile', async (route) => {
    profileRequests += 1
    const requestNumber = profileRequests
    if (requestNumber === 1) await accountAGate
    const account = requestNumber === 1 ? 'account-a' : 'account-b'
    try {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: jsonBody({
          username: account,
          email: `${account}@example.com`,
          themePreference: 'system',
        }),
      })
    } finally {
      if (requestNumber === 1) finishAccountA()
    }
  })
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody({ accountId: 2 }),
    })
  })

  try {
    await page.goto('/interview')
    await page.getByRole('button', { name: '设置' }).click()
    await expect(page.getByText('正在读取账号资料…')).toBeVisible()
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('用户名').fill('account-b')
    await page.locator('#auth-password').fill('password')
    await page.getByRole('button', { name: '登录', exact: true }).last().click()
    await expect(page).toHaveURL(/\/interview$/)
    await page.getByRole('button', { name: '设置' }).click()
    await expect.poll(() => profileRequests).toBe(2)
    await expect(page.getByLabel('用户名')).toHaveValue('account-b')
    releaseAccountA()
    await accountAFinished
    await expect(page.getByLabel('用户名')).toHaveValue('account-b')
    await expect(page.getByText('account-a')).toHaveCount(0)
  } finally {
    releaseAccountA()
  }
})

test('@smoke releases voice resources and returns to text mode after a terminal error', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请回答问题。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await installVoiceHarness(page)

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '切换到语音输入' }).click()
  const talk = page.locator('.ui-button--hold')
  await talk.dispatchEvent('pointerdown')
  /* Held: the meter replaces the words inside the same box, so the control keeps its
     name and its width and only the state moves. */
  await expect(talk).toHaveAccessibleName('松开发送')
  await expect(talk.locator('.voice-meter')).toBeVisible()
  await expect(talk.locator('.ui-button__label')).toHaveCSS('opacity', '0')
  await page.evaluate(() => {
    const socket = (
      window as unknown as { voiceSocket: { onmessage: (event: MessageEvent) => void } }
    ).voiceSocket
    socket.onmessage(
      new MessageEvent('message', {
        data: JSON.stringify({ type: 'error', message: '语音服务终止' }),
      }),
    )
  })

  await expect(page.getByPlaceholder('输入回答…')).toBeVisible()
  await expect(page.getByText('语音服务终止')).toBeVisible()
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as { voiceState: { closed: number } }).voiceState.closed,
      ),
    )
    .toBeGreaterThan(0)
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as unknown as { voiceState: { stoppedTracks: number } }).voiceState.stoppedTracks,
      ),
    )
    .toBeGreaterThan(0)
})

test('@smoke keeps a voice transcript in the composer until the candidate sends it', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请回答问题。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await installVoiceHarness(page)

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '切换到语音输入' }).click()
  await page.evaluate(() => {
    const socket = (
      window as unknown as { voiceSocket: { onmessage: (event: MessageEvent) => void } }
    ).voiceSocket
    socket.onmessage(
      new MessageEvent('message', {
        data: JSON.stringify({ type: 'user_text', text: '我会用支付单号作为幂等键。' }),
      }),
    )
  })

  /* What the microphone heard is a draft: it lands in the text box and stays there, so a
     mis-transcription can be fixed before it becomes an interview answer. */
  await expect(page.getByLabel('面试回答')).toHaveValue('我会用支付单号作为幂等键。')
  await expect(
    page.locator('.message-bubble-body', { hasText: '我会用支付单号作为幂等键。' }),
  ).toHaveCount(0)

  await page.getByRole('button', { name: '发送' }).click()
  await expect(
    page.locator('.message-bubble-body', { hasText: '我会用支付单号作为幂等键。' }),
  ).toBeVisible()
})

test('@smoke releases media that arrives after voice mode closes', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请回答问题。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await installVoiceHarness(page, true)

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '切换到语音输入' }).click()
  const talk = page.locator('.ui-button--hold')
  await talk.dispatchEvent('pointerdown')
  await page.evaluate(() => {
    const socket = (
      window as unknown as { voiceSocket: { onmessage: (event: MessageEvent) => void } }
    ).voiceSocket
    socket.onmessage(
      new MessageEvent('message', {
        data: JSON.stringify({ type: 'error', message: '语音服务终止' }),
      }),
    )
  })
  await expect(page.getByPlaceholder('输入回答…')).toBeVisible()
  await page.evaluate(() => (window as unknown as { releaseMedia: () => void }).releaseMedia())

  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as unknown as { voiceState: { stoppedTracks: number } }).voiceState.stoppedTracks,
      ),
    )
    .toBe(1)
  expect(
    await page.evaluate(
      () =>
        (window as unknown as { voiceState: { recorderStarts: number } }).voiceState.recorderStarts,
    ),
  ).toBe(0)
})

test('@byok sends the exact custom provider DTO', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  await page.getByLabel('接入方式').click()
  await page.getByRole('option', { name: 'OpenAI Chat Completions' }).click()
  await page.getByLabel('Base URL').fill('https://api.openai.com/v1/chat/completions/')
  await page.getByLabel('模型', { exact: true }).fill('account-discovered-model')
  await page.getByLabel('API Key', { exact: true }).fill('sk-test')
  await page.getByRole('combobox', { name: '最大回复长度' }).click()
  await page.getByRole('option', { name: '长回复 · 8,192 tokens' }).click()
  await page.getByRole('button', { name: '保存设置' }).click()
  await expect(page.getByText('LLM 配置已保存')).toBeVisible()
  const save = state.requests.find(
    (request) => request.path === '/api/llm/config' && request.method === 'PUT',
  )
  expect(save?.body).toEqual({
    provider: 'openai-chat-completions',
    customEndpointUrl: 'https://api.openai.com/v1',
    model: 'account-discovered-model',
    apiKey: 'sk-test',
    reasoningLevel: 'AUTO',
    maxOutputTokens: 8192,
    fallbackModels: [],
  })
})

test('@byok discovers selected custom model reasoning levels from the backend', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  await page.getByLabel('接入方式').click()
  await page.getByRole('option', { name: 'OpenAI Chat Completions' }).click()
  await page.getByLabel('Base URL').fill('https://api.openai.com/v1')
  await page.getByLabel('API Key', { exact: true }).fill('sk-test')
  await page.getByRole('button', { name: '检测模型' }).click()

  await page.getByLabel('模型', { exact: true }).click()
  await page.getByRole('option', { name: 'account-discovered-model' }).click()

  await expect
    .poll(() =>
      state.requests.some((request) => request.path === '/api/llm/config/discover-capabilities'),
    )
    .toBe(true)
  await page.getByRole('combobox', { name: '思考深度' }).click()
  await expect(page.getByRole('option', { name: '默认', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '低', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '中', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '高', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '超高', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '最大', exact: true })).toBeVisible()
})

test('@byok keeps the backend conservative capability when a selected-model probe is unavailable', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.route('**/api/llm/config/discover-capabilities', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'Service Unavailable',
        status: 503,
        detail: 'capability probe unavailable',
      }),
    })
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  await page.getByLabel('接入方式').click()
  await page.getByRole('option', { name: 'OpenAI Responses' }).click()
  await page.getByLabel('Base URL').fill('https://api.openai.com/v1')
  await page.getByLabel('API Key', { exact: true }).fill('sk-test')
  await page.getByRole('button', { name: '检测模型' }).click()
  await page.getByLabel('模型', { exact: true }).click()
  await page.getByRole('option', { name: 'account-discovered-model' }).click()

  await page.getByRole('combobox', { name: '思考深度' }).click()
  await expect(page.getByRole('option', { name: '默认', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '高', exact: true })).toHaveCount(0)
})

for (const outcome of ['success', 'error'] as const) {
  test(`@byok ignores a late model-discovery ${outcome} after switching provider`, async ({
    page,
  }) => {
    const state: ApiState = { requests: [] }
    await installApi(page, state)
    let releaseDiscovery!: () => void
    let markDiscoveryStarted!: () => void
    const discoveryGate = new Promise<void>((resolve) => {
      releaseDiscovery = resolve
    })
    const discoveryStarted = new Promise<void>((resolve) => {
      markDiscoveryStarted = resolve
    })
    await page.route('**/api/llm/config/discover-models', async (route) => {
      markDiscoveryStarted()
      await discoveryGate
      if (outcome === 'error') {
        await fulfillProblem(route, 500, 'old_provider_failure', '旧供应商探测失败')
        return
      }
      await fulfillJson(route, {
        baseUrl: 'https://old.example/v1',
        models: [customModelCapability('openai-chat-completions', 'stale-discovered-model')],
      })
    })
    await page.goto('/interview')
    await page.getByRole('button', { name: '设置' }).click()
    await page.getByRole('button', { name: '模型管理' }).click()
    await page.getByLabel('接入方式').click()
    await page.getByRole('option', { name: 'OpenAI Chat Completions' }).click()
    await page.getByLabel('Base URL').fill('https://old.example/v1')
    await page.getByRole('button', { name: '检测模型' }).click()
    await discoveryStarted

    await page.getByLabel('接入方式').click()
    await page.getByRole('option', { name: 'Anthropic Messages' }).click()
    const lateResponse = page.waitForResponse((response) =>
      response.url().endsWith('/api/llm/config/discover-models'),
    )
    releaseDiscovery()
    await lateResponse
    await expect(page.getByRole('button', { name: '检测模型' })).not.toHaveAttribute('aria-busy')

    await expect(page.getByLabel('接入方式')).toContainText('Anthropic Messages')
    await expect(page.getByLabel('模型', { exact: true })).toHaveJSProperty('tagName', 'INPUT')
    await expect(page.getByLabel('模型', { exact: true })).toHaveValue('')
    await expect(page.getByText('旧供应商探测失败')).toHaveCount(0)
  })
}

for (const outcome of ['success', 'error'] as const) {
  test(`@byok ignores a late selected-model capability ${outcome}`, async ({ page }) => {
    const state: ApiState = { requests: [] }
    await installApi(page, state)
    await page.route('**/api/llm/config/discover-models', async (route) => {
      await fulfillJson(route, {
        baseUrl: 'https://models.example/v1',
        models: [
          customModelCapability('openai-chat-completions', 'old-model'),
          customModelCapability('openai-chat-completions', 'new-model', ['AUTO', 'LOW']),
        ],
      })
    })
    let releaseOldProbe!: () => void
    let markOldProbeStarted!: () => void
    const oldProbeGate = new Promise<void>((resolve) => {
      releaseOldProbe = resolve
    })
    const oldProbeStarted = new Promise<void>((resolve) => {
      markOldProbeStarted = resolve
    })
    await page.route('**/api/llm/config/discover-capabilities', async (route) => {
      const model = (route.request().postDataJSON() as { model: string }).model
      if (model === 'new-model') {
        await fulfillJson(
          route,
          customModelCapability('openai-chat-completions', 'new-model', ['AUTO', 'LOW']),
        )
        return
      }
      markOldProbeStarted()
      await oldProbeGate
      if (outcome === 'error') {
        await fulfillProblem(route, 500, 'old_model_failure', '旧模型探测失败')
        return
      }
      await fulfillJson(
        route,
        customModelCapability('openai-chat-completions', 'old-model', ['AUTO', 'MAX']),
      )
    })
    await page.goto('/interview')
    await page.getByRole('button', { name: '设置' }).click()
    await page.getByRole('button', { name: '模型管理' }).click()
    await page.getByLabel('接入方式').click()
    await page.getByRole('option', { name: 'OpenAI Chat Completions' }).click()
    await page.getByLabel('Base URL').fill('https://models.example/v1')
    await page.getByRole('button', { name: '检测模型' }).click()
    await page.getByLabel('模型', { exact: true }).click()
    await page.getByRole('option', { name: 'old-model', exact: true }).click()
    await oldProbeStarted

    const newProbeResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/llm/config/discover-capabilities') &&
        (response.request().postDataJSON() as { model?: string } | null)?.model === 'new-model',
    )
    await page.getByLabel('模型', { exact: true }).click()
    await page.getByRole('option', { name: 'new-model', exact: true }).click()
    await newProbeResponse
    await page.getByRole('combobox', { name: '思考深度' }).click()
    await expect(page.getByRole('option', { name: '默认', exact: true })).toBeVisible()
    await expect(page.getByRole('option', { name: '低', exact: true })).toBeVisible()
    await page.keyboard.press('Escape')

    const oldProbeResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/llm/config/discover-capabilities') &&
        (response.request().postDataJSON() as { model?: string } | null)?.model === 'old-model',
    )
    releaseOldProbe()
    await (await oldProbeResponse).finished()
    await page.evaluate(
      () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())),
    )
    await page.getByRole('combobox', { name: '思考深度' }).click()
    await expect(page.getByRole('option', { name: '默认', exact: true })).toBeVisible()
    await expect(page.getByRole('option', { name: '低', exact: true })).toBeVisible()
    await expect(page.getByRole('option', { name: '最大', exact: true })).toHaveCount(0)
    await expect(page.getByText('旧模型探测失败')).toHaveCount(0)
  })
}

const report = JSON.stringify({
  summary: {
    fitAssessment: '具备岗位所需的工程能力',
    actionRecommendation: '继续训练系统设计',
    overallRisk: '容量估算证据不足',
  },
  scores: { technical: 8.2, expression: 7.6, logic: 8.4, overall: 8.1 },
  stagePerformances: [
    {
      stageName: 'technical',
      score: 8.2,
      summary: '技术基础扎实',
      positiveSignals: ['边界清晰'],
      negativeSignals: ['容量数据不足'],
      improvementSuggestions: ['补充量化依据'],
    },
    {
      stageName: 'deep_dive',
      score: 7.6,
      summary: '追问中的取舍说明仍可加强',
      positiveSignals: ['能够识别关键约束'],
      negativeSignals: ['缺少成本数据'],
      improvementSuggestions: ['补充方案对比'],
    },
  ],
  questionReviews: [
    {
      stageName: 'technical',
      question: '如何拆分服务？',
      answerSummary: '按业务能力划分',
      score: 8,
      scoringReason: '边界意识明确',
      improvementSuggestion: '补充演进策略',
    },
  ],
  strengths: ['边界意识'],
  weaknesses: ['容量估算'],
  trainingPlan: {
    threeDay: ['练习容量估算'],
    sevenDay: ['完成系统设计复盘'],
    nextInterviewFocus: ['量化方案'],
  },
  finalAdvice: '保持结构化表达。',
})

test('@smoke renders structured reports without resume mutation controls', async ({ page }) => {
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'finished',
      currentStage: 'closing',
      model: 'deepseek-v4-flash',
      reasoningLevel: 'HIGH',
      summaryReport: report,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '本场面试已结束。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')
  await expect(page.getByText('deepseek-v4-flash · 高', { exact: true })).toBeVisible()
  await expect(page.getByText('本场模型已锁定', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '报告' }).click()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await expect(page.getByText('8.1')).toBeVisible()
  await expect(page.getByRole('button', { name: '打印报告' })).toHaveCount(1)
  const viewToggle = page.getByRole('group', { name: '工作区视图' })
  await expect(viewToggle.getByRole('button', { name: '面试' })).toHaveCount(1)
  await expect(viewToggle.getByRole('button', { name: '报告' })).toHaveCount(1)
  await expect(page.locator('.status-badge')).toHaveCount(0)
  const sharedUiMetrics = await page.evaluate(() => {
    const parse = (value: string) => {
      const channels = value
        .match(/[\d.]+/g)!
        .slice(0, 3)
        .map(Number)
      return value.startsWith('color(srgb') ? channels.map((channel) => channel * 255) : channels
    }
    const luminance = (value: string) => {
      const [r, g, b] = parse(value).map((channel) => {
        const normalized = channel / 255
        return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
      })
      return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    const contrast = (element: Element) => {
      const style = getComputedStyle(element)
      const foreground = luminance(style.color)
      const background = luminance(style.backgroundColor)
      return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05)
    }
    const primary = document.querySelector<HTMLElement>('.sidebar-action-primary')!
    const activeProbe = document.createElement('button')
    activeProbe.className = 'session-row is-active'
    document.body.append(activeProbe)
    const durations = getComputedStyle(primary)
      .transitionDuration.split(',')
      .map((value) => Number.parseFloat(value) * (value.includes('ms') ? 1 : 1000))
    const metrics = {
      primaryContrast: contrast(primary),
      activeContrast: contrast(activeProbe),
      longestInteractionTransition: Math.max(...durations),
    }
    activeProbe.remove()
    return metrics
  })
  expect(sharedUiMetrics.primaryContrast).toBeGreaterThanOrEqual(4.5)
  expect(sharedUiMetrics.activeContrast).toBeGreaterThanOrEqual(4.5)
  expect(sharedUiMetrics.longestInteractionTransition).toBeLessThanOrEqual(150)
  const reportTypography = await page
    .locator('[data-slot="structured-report"]')
    .evaluate((surface) => {
      const eyebrow = surface.querySelector<HTMLElement>('[data-slot="report-hero"] p')!
      const sectionTitle = surface.querySelector<HTMLElement>('[data-slot="structured-report"] h2')!
      const adviceBody = surface.querySelector<HTMLElement>('[data-slot="report-advice"] > p')!
      const titleStyle = getComputedStyle(sectionTitle)
      return {
        eyebrowFamily: getComputedStyle(eyebrow).fontFamily,
        adviceBodyFamily: getComputedStyle(adviceBody).fontFamily,
        sectionTitleLineHeight: Number.parseFloat(titleStyle.lineHeight),
        sectionTitleSize: Number.parseFloat(titleStyle.fontSize),
      }
    })
  expect(reportTypography.eyebrowFamily).toContain('Lora')
  expect(reportTypography.adviceBodyFamily).toContain('Inter')
  expect(
    reportTypography.sectionTitleLineHeight / reportTypography.sectionTitleSize,
  ).toBeLessThanOrEqual(1.3)
  const stageNavigation = page.getByRole('group', { name: '阶段复盘导航' })
  await expect(stageNavigation).toContainText('1 / 2')
  await expect(page.locator('[data-slot="stage-performance"]')).toHaveCount(2)
  await expect(page.locator('[data-slot="stage-performance"][data-state="active"]')).toContainText(
    '技术问答',
  )
  await expect(
    page.locator('[data-slot="stage-performance"][data-state="active"] [data-slot="stage-score"]'),
  ).toHaveCSS('font-family', /Lora/)
  await expect(
    page.locator('[data-slot="stage-performance"][data-state="active"] h4').first(),
  ).toHaveCSS('font-family', /Lora/)
  await stageNavigation.getByRole('button', { name: '下一阶段' }).click()
  await expect(stageNavigation).toContainText('2 / 2')
  await expect(page.locator('[data-slot="stage-performance"][data-state="active"]')).toContainText(
    '深度追问',
  )

  /* The question review is a second carousel on the same page with its own index state, so
     the stage one passing says nothing about it. This report carries a single review, which
     is the case that matters: both ends of the rail have to be spent at once. */
  const reviewNavigation = page.getByRole('group', { name: '逐题复盘导航' })
  await expect(reviewNavigation).toContainText('1 / 1')
  await expect(page.locator('[data-slot="question-review"]')).toHaveCount(1)
  await expect(reviewNavigation.getByRole('button', { name: '上一题' })).toBeDisabled()
  await expect(reviewNavigation.getByRole('button', { name: '下一题' })).toBeDisabled()
  await expect(stageNavigation).toContainText('2 / 2')

  const reviewSurfaces = await page.evaluate(() => {
    const stage = getComputedStyle(
      document.querySelector('[data-slot="stage-performance"][data-state="active"]')!,
    )
    const question = getComputedStyle(document.querySelector('[data-slot="question-review"]')!)
    return {
      background: [stage.backgroundColor, question.backgroundColor],
      radius: [stage.borderRadius, question.borderRadius],
      padding: [stage.paddingInlineStart, question.paddingInlineStart],
    }
  })
  expect(new Set(reviewSurfaces.background).size).toBe(1)
  expect(new Set(reviewSurfaces.radius).size).toBe(1)
  expect(new Set(reviewSurfaces.padding).size).toBe(1)

  await page.setViewportSize({ width: 880, height: 781 })
  for (const selector of [
    '[data-slot="stage-performance"][data-state="active"] [data-slot="stage-signals"]',
    '[data-slot="report-traits"] > div',
    '[data-slot="training-plan-grid"]',
  ]) {
    const adaptiveColumns = await page
      .locator(selector)
      .first()
      .evaluate((grid) => {
        const style = getComputedStyle(grid)
        const minimum = Number.parseFloat(
          getComputedStyle(document.documentElement).getPropertyValue(
            '--layout-document-column-min-inline-size',
          ),
        )
        return style.gridTemplateColumns
          .split(' ')
          .every((column) => Number.parseFloat(column) >= minimum)
      })
    expect(adaptiveColumns).toBe(true)
  }
  await page.emulateMedia({ media: 'print' })
  await page.locator('body').evaluate((body) => body.classList.add('is-printing-report'))
  await expect(page.locator('.app-layout__main')).toHaveCSS('overflow', 'visible')
  await expect(page.locator('[data-slot="stage-performance"]').first()).toBeVisible()
  await expect(page.locator('[data-slot="stage-performance"]').last()).toBeVisible()
  await page.locator('body').evaluate((body) => body.classList.remove('is-printing-report'))
  await page.emulateMedia({ media: 'screen' })
  await page.evaluate(() => {
    window.print = () => {
      document.body.dataset.printCalled = 'true'
    }
  })
  await page.getByRole('button', { name: '打印报告' }).click()
  await expect(page.locator('body')).toHaveAttribute('data-print-called', 'true')
  await expect(page.locator('body')).not.toHaveClass(/is-printing-report/)
})

test('@smoke renders analytics charts and recent-score labels from the React dashboard', async ({
  page,
}) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.route('**/api/analytics/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/radar')
      ? { technical: 7.5, expression: 6.5, logic: 8, sessionCount: 2 }
      : path.endsWith('/trend')
        ? [
            {
              sessionId: 1,
              createdAt: '2026-08-01T00:00:00Z',
              technical: 7,
              expression: 6,
              logic: 8,
            },
            {
              sessionId: 2,
              createdAt: '2026-08-08T00:00:00Z',
              technical: 8,
              expression: 7,
              logic: 8,
            },
          ]
        : [{ category: '容量估算', count: 2, descriptions: ['补充量化依据。'] }]
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: jsonBody(data),
    })
  })
  await page.goto('/analytics')

  await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '分数趋势' })).toBeVisible()
  await expect(page.getByRole('img', { name: /技术能力 7\.5/ })).toHaveCount(1)
  await expect(page.getByRole('img', { name: '最近 2 场面试的分数趋势' })).toHaveCount(1)
  await expect(page.getByText('最近 2 场均分')).toHaveCount(3)
  await expect(page.getByText('结构')).toBeVisible()
  await expect(page.getByText('走势')).toBeVisible()
  await expect(page.getByText('聚合')).toBeVisible()

  const typography = await page
    .locator('[data-slot="score-card"]')
    .first()
    .evaluate((card) => {
      const label = getComputedStyle(card.querySelector('[data-slot="score-label"]')!)
      const value = getComputedStyle(card.querySelector('[data-slot="score-value"]')!)
      const meta = getComputedStyle(card.querySelector('[data-slot="score-meta"]')!)
      return {
        label: {
          family: label.fontFamily,
          size: label.fontSize,
          weight: label.fontWeight,
        },
        value: {
          family: value.fontFamily,
          numeric: value.fontVariantNumeric,
        },
        meta: {
          family: meta.fontFamily,
          size: meta.fontSize,
          weight: meta.fontWeight,
        },
      }
    })
  expect(typography.label).toMatchObject({ size: '14px', weight: '500' })
  expect(typography.label.family).toContain('Lora')
  expect(typography.value.family).toContain('Lora')
  expect(typography.value.numeric).toBe('tabular-nums')
  expect(typography.meta).toMatchObject({ size: '12px', weight: '400' })
  expect(typography.meta.family).toContain('Inter')

  const weaknessLayout = await page.locator('[data-slot="weakness-item"]').evaluate((item) => {
    const title = item.querySelector<HTMLElement>('[data-slot="weakness-title"]')!
    const summary = item.querySelector<HTMLElement>('[data-slot="weakness-summary"]')!
    const descriptions = item.querySelector<HTMLElement>('[data-slot="weakness-descriptions"]')!
    return {
      headingLayout: getComputedStyle(title.parentElement!).display,
      titleLeft: title.getBoundingClientRect().left,
      summaryLeft: summary.getBoundingClientRect().left,
      descriptionLeft: descriptions.getBoundingClientRect().left,
      descriptionListStyle: getComputedStyle(descriptions).listStyleType,
    }
  })
  expect(weaknessLayout.headingLayout).toBe('grid')
  expect(weaknessLayout.summaryLeft).toBeGreaterThan(weaknessLayout.titleLeft)
  expect(Math.abs(weaknessLayout.descriptionLeft - weaknessLayout.titleLeft)).toBeLessThan(1)
  expect(weaknessLayout.descriptionListStyle).toBe('none')
})

test('@smoke degrades malformed structured reports to safe plain text', async ({ page }) => {
  const malformed = JSON.stringify({
    summary: {
      fitAssessment: '不能伪造评分',
      actionRecommendation: '展示原始结果',
      overallRisk: '评分字段缺失',
    },
    scores: { expression: 7, logic: 8 },
    stagePerformances: [{ stageName: 'invented-stage', summary: '非法阶段' }],
    finalAdvice: '保留原始事实',
  })
  const state: ApiState = {
    requests: [],
    session: {
      sessionId: 11,
      targetPosition: '平台工程师',
      status: 'finished',
      currentStage: 'closing',
      summaryReport: malformed,
      stages: [],
      messages: [],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '报告' }).click()

  await expect(page.locator('[data-slot="report-plain-text"]')).toContainText('"expression":7')
  await expect(page.getByText('6.0')).toHaveCount(0)
  await expect(page.getByText('破冰')).toHaveCount(0)
})

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}

/* The generating state is what a candidate stares at after 结束面试, and until now nothing
   in the product rendered it under test — only the gallery's frozen copy did. A session that
   is 'generating' with no report yet is the whole difference between that surface and the
   report, so it is worth locking on its own. */
test('@smoke shows the generating surface while a finished session waits for its report', async ({
  page,
}) => {
  const state: ApiState = {
    requests: [],
    sessions: [{ sessionId: 12, targetPosition: 'Java 后端工程师', status: 'generating' }],
    session: {
      sessionId: 12,
      targetPosition: 'Java 后端工程师',
      status: 'generating',
      currentStage: 'closing',
      summaryReport: null,
      stages: [],
      messages: [{ id: 1, role: 'assistant', content: '请先介绍一下你自己。' }],
      resumeId: 1,
      positionId: 1,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=12')

  const card = page.locator('.generating-card')
  await expect(card).toBeVisible()
  await expect(card.getByRole('heading', { name: 'AI 评估报告生成中…' })).toBeVisible()
  await expect(card.locator('[data-slot="rose-three-loader"]')).toBeVisible()
  await expect(card.locator('.generating-progress-track')).toHaveCount(0)
  // The composer and the report are both absent: this surface replaces them entirely.
  await expect(page.locator('[data-slot="prompt-bar-surface"]')).toHaveCount(0)
  await expect(page.locator('[data-slot="workspace-report"]')).toHaveCount(0)
  // The card is centred on a plain sheet by its owner, not by the page improvising.
  await expect(page.locator('.generating-card')).toBeInViewport()
})
