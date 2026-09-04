import { expect, test, type Page, type Route } from '@playwright/test'
import { installAnonymousSession } from './auth-bootstrap'

type ApiState = {
  sessions?: unknown[]
  session?: Record<string, unknown>
  requests: Array<{ path: string; method: string; body: unknown }>
}

const deepSeekCapability = (model = 'deepseek-v4-pro') => ({
  provider: 'deepseek',
  model,
  reasoning: true,
  structuredOutput: true,
  toolCalling: true,
  streaming: true,
  vision: false,
  multilingual: true,
  longContext: true,
  embedding: false,
  nativeRealtimeVoice: false,
  supportedReasoningLevels: ['AUTO', 'LOW', 'HIGH', 'MAX'],
})

const customCapability = (
  provider: string,
  model: string,
  supportedReasoningLevels: Array<'AUTO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX'> = ['AUTO'],
) => ({
  provider,
  model,
  reasoning: supportedReasoningLevels.length > 1,
  structuredOutput: false,
  toolCalling: false,
  streaming: true,
  vision: false,
  multilingual: false,
  longContext: false,
  embedding: false,
  nativeRealtimeVoice: false,
  supportedReasoningLevels,
})

const providers = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    customEndpoint: false,
    models: [deepSeekCapability(), deepSeekCapability('deepseek-v4-flash')],
  },
  {
    providerKey: 'openai-responses',
    displayName: 'OpenAI Responses',
    customEndpoint: true,
    models: [],
  },
  {
    providerKey: 'openai-chat-completions',
    displayName: 'OpenAI Chat Completions',
    customEndpoint: true,
    models: [],
  },
  {
    providerKey: 'anthropic-messages',
    displayName: 'Anthropic Messages',
    customEndpoint: true,
    models: [],
  },
]

const ok = (data: unknown) => JSON.stringify({ code: 200, message: 'ok', data })

async function installApi(page: Page, state: ApiState) {
  await page.route(/^https?:\/\/[^/]+\/api\//, async (route) => respond(route, state))
}

async function installVoiceHarness(page: Page, delayMedia = false) {
  await page.addInitScript(({ delayed }) => {
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
    Object.defineProperty(window, 'MediaRecorder', { configurable: true, value: MockMediaRecorder })
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
  }, { delayed: delayMedia })
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
  let data: unknown = null
  if (path === '/api/auth/me') data = { accountId: 1, username: 'prelude' }
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
        customCapability(
          (body as { provider?: string }).provider ?? 'openai-chat-completions',
          'account-discovered-model',
        ),
      ],
    }
  else if (path === '/api/llm/config/discover-capabilities' && method === 'POST')
    data = customCapability(
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
          : customCapability(
              (body as { provider?: string }).provider ?? 'openai-chat-completions',
              (body as { model?: string }).model ?? 'account-discovered-model',
              (body as { reasoningLevel?: string }).reasoningLevel === 'HIGH'
                ? ['AUTO', 'HIGH']
                : ['AUTO'],
            ),
    }
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: ok(data),
  })
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
    requestedModel: 'deepseek-v4-pro',
    attachmentIds: [51],
  })
})

test('@smoke presents request failures as a dismissible top system toast', async ({ page }) => {
  await installAnonymousSession(page)
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ type: 'about:blank', title: 'service_unavailable', status: 503, detail: '服务暂不可用', code: 'service_unavailable' }),
    })
  })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.locator('#auth-password').fill('password')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '服务暂不可用' })
  await toast.evaluate((element) => {
    const closeButton = element.querySelector<HTMLButtonElement>('button[aria-label="关闭系统提示"]')
    if (!closeButton) throw new Error('系统提示缺少可访问的关闭按钮')
    closeButton.click()
  })
  await expect(toast).toHaveCount(0)
  await expect(page.locator('.auth-form > .notice--error')).toHaveCount(0)
  await expect(page).toHaveURL(/\/login$/)
})

test('@smoke keeps authentication validation out of the form layout', async ({ page }) => {
  await installAnonymousSession(page)
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '密码至少需要 6 个字符' })
  await expect(toast).toBeAttached()
  await expect(page.locator('#auth-password')).toBeFocused()
  await expect(page.locator('.auth-form > .notice--error')).toHaveCount(0)
})

test('@smoke routes prompt bar management actions into global settings', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')

  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: /选择简历/ }).hover()
  const chooser = page.waitForEvent('filechooser')
  await page.getByRole('menuitem', { name: '新建简历' }).click()
  await chooser
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

  await page.getByRole('button', { name: /模型：deepseek-v4-pro，思考深度：默认/ }).click()
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
      body: ok({
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
  const idleWidth = (await save.boundingBox())!.width
  await save.click()
  await expect(save).toHaveAttribute('aria-busy', 'true')
  const loading = await save.evaluate((button) => {
    const control = button.getBoundingClientRect()
    const spinner = button.querySelector<HTMLElement>('.button-spinner')!.getBoundingClientRect()
    const content = button.querySelector<HTMLElement>('.prelude-button__content')!
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
  await expect(save.locator('.prelude-button__content')).toHaveCSS('opacity', '1')
  expect((await save.boundingBox())!.width).toBe(idleWidth)
})

test('@smoke updates the prompt model depth before the save request completes', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.route('**/api/llm/config', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    await new Promise((resolve) => setTimeout(resolve, 400))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({
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

  let modelTrigger = page.getByRole('button', { name: /模型：/ })
  await modelTrigger.click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '高', exact: true }).click()

  modelTrigger = page.getByRole('button', { name: /模型：/ })
  expect(await modelTrigger.textContent()).toContain('deepseek-v4-pro · 高')
  await expect(page.getByText('模型配置已更新')).toBeVisible()

  await modelTrigger.click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  const resetRequest = page.waitForRequest(
    (request) => request.url().endsWith('/api/llm/config') && request.method() === 'PUT',
  )
  await page.getByRole('menuitemradio', { name: '默认', exact: true }).click()
  expect(await page.getByRole('button', { name: /模型：/ }).textContent()).toContain(
    'deepseek-v4-pro · 默认',
  )
  expect((await resetRequest).postDataJSON()).toMatchObject({ reasoningLevel: 'AUTO' })
})

test('@smoke waits for model configuration persistence before starting an interview', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)

  let releaseSave!: () => void
  const saveReleased = new Promise<void>((resolve) => {
    releaseSave = resolve
  })
  let signalSaveStarted!: () => void
  const saveStarted = new Promise<void>((resolve) => {
    signalSaveStarted = resolve
  })
  await page.route('**/api/llm/config', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    signalSaveStarted()
    await saveReleased
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({
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
  await selectContext(page, '选择简历', '作品集简历.pdf')
  await selectContext(page, '选择岗位', '前端工程师')
  const start = page.getByRole('button', { name: '开始面试' })
  await expect(start).toBeEnabled()

  await page.getByRole('button', { name: /模型：/ }).click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '高', exact: true }).click()
  await saveStarted

  await expect(start).toBeDisabled()
  expect(state.requests.some((request) => request.path === '/api/interview/start')).toBe(false)

  releaseSave()
  await expect(page.getByText('模型配置已更新')).toBeVisible()
  await expect(start).toBeEnabled()
})

test('@smoke persists pinned and hidden sessions per account', async ({ page }) => {
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
  await expect
    .poll(() =>
      page.evaluate(() => localStorage.getItem('prelude-interview-session-preferences:1')),
    )
    .toContain('"pinnedIds":[11]')
  await page.getByRole('button', { name: '删除会话' }).first().click()
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByText('平台工程师')).toHaveCount(0)
  await expect
    .poll(() =>
      page.evaluate(() => localStorage.getItem('prelude-interview-session-preferences:1')),
    )
    .toContain('"hiddenIds":[11]')
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
        body: JSON.stringify({ type: 'about:blank', title: 'service_unavailable', status: 503, detail: '会话 B 暂时不可用', code: 'service_unavailable' }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({
        sessionId,
        targetPosition: sessionId === 11 ? '会话 A' : '会话 B',
        status: 'ongoing',
        currentStage: 'technical',
        summaryReport: null,
        stages: [],
        messages: [
          { id: sessionId, role: 'assistant', content: sessionId === 11 ? 'A 正在显示' : 'B 已加载' },
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

test('@smoke prevents a late session request from overwriting the latest target', async ({ page }) => {
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
  const bGate = new Promise<void>((resolve) => {
    releaseB = resolve
  })
  await page.route('**/api/interview/*/messages', async (route) => {
    const sessionId = Number(new URL(route.request().url()).pathname.split('/').at(-2))
    if (sessionId === 12) await bGate
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({
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
  })

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '打开会话 会话 B' }).click()
  await page.getByRole('button', { name: '打开会话 会话 C' }).click()
  await expect(page).toHaveURL(/session=13/)
  releaseB()
  await page.waitForTimeout(100)
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
      body: ok({
        sessionId,
        targetPosition: sessionId === 11 ? '会话 A' : '会话 B',
        status: 'ongoing',
        currentStage: 'technical',
        summaryReport: null,
        stages: [],
        messages:
          sessionId === 11 ? [{ id: 11, role: 'assistant', content: 'A 正在显示' }] : [],
        resumeId: 1,
        positionId: 1,
        attachments: [],
      }),
    })
  })

  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '打开会话 会话 B' }).click()
  await expect(page).toHaveURL(/session=12/)
  const autoStartRequests = () =>
    state.requests.filter(
      (request) => request.path === '/api/interview/12/chat' && request.method === 'POST',
    )
  await expect.poll(() => autoStartRequests()).toHaveLength(1)
  await page.waitForTimeout(100)
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
  await page.route('**/api/interview/11/chat', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 150))
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ type: 'about:blank', title: 'service_unavailable', status: 503, detail: '流式服务暂不可用', code: 'service_unavailable' }),
    })
  })

  await page.goto('/interview?session=11')
  await page.getByPlaceholder('输入回答…').fill('这条乐观消息必须回滚')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('这条乐观消息必须回滚')).toBeVisible()
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
      body: JSON.stringify({ type: 'about:blank', title: 'authentication_required', status: 401, detail: '登录已失效', code: 'authentication_required' }),
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
  const accountAGate = new Promise<void>((resolve) => {
    releaseAccountA = resolve
  })
  await page.route('**/api/user/profile', async (route) => {
    profileRequests += 1
    const requestNumber = profileRequests
    if (requestNumber === 1) await accountAGate
    const account = requestNumber === 1 ? 'account-a' : 'account-b'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok({ username: account, email: `${account}@example.com`, themePreference: 'system' }),
    })
  })
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: ok({ accountId: 2 }) })
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
    await page.waitForTimeout(100)
    await expect(page.getByLabel('用户名')).toHaveValue('account-b')
    await expect(page.getByText('account-a')).toHaveCount(0)
  } finally {
    releaseAccountA()
  }
})

test('@smoke releases voice resources and returns to text mode after a terminal error', async ({ page }) => {
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
  const talk = page.locator('.prompt-bar__voice-button')
  await talk.dispatchEvent('pointerdown')
  await expect(talk).toHaveText('松开发送')
  await page.evaluate(() => {
    const socket = (window as unknown as { voiceSocket: { onmessage: (event: MessageEvent) => void } })
      .voiceSocket
    socket.onmessage(
      new MessageEvent('message', { data: JSON.stringify({ type: 'error', message: '语音服务终止' }) }),
    )
  })

  await expect(page.getByPlaceholder('输入回答…')).toBeVisible()
  await expect(page.getByText('语音服务终止')).toBeVisible()
  await expect
    .poll(() =>
      page.evaluate(() => (window as unknown as { voiceState: { closed: number } }).voiceState.closed),
    )
    .toBeGreaterThan(0)
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as { voiceState: { stoppedTracks: number } }).voiceState.stoppedTracks,
      ),
    )
    .toBeGreaterThan(0)
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
  const talk = page.locator('.prompt-bar__voice-button')
  await talk.dispatchEvent('pointerdown')
  await page.evaluate(() => {
    const socket = (window as unknown as { voiceSocket: { onmessage: (event: MessageEvent) => void } })
      .voiceSocket
    socket.onmessage(
      new MessageEvent('message', { data: JSON.stringify({ type: 'error', message: '语音服务终止' }) }),
    )
  })
  await expect(page.getByPlaceholder('输入回答…')).toBeVisible()
  await page.evaluate(() => (window as unknown as { releaseMedia: () => void }).releaseMedia())

  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as { voiceState: { stoppedTracks: number } }).voiceState.stoppedTracks,
      ),
    )
    .toBe(1)
  expect(
    await page.evaluate(
      () => (window as unknown as { voiceState: { recorderStarts: number } }).voiceState.recorderStarts,
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

test('@byok discovers selected custom model reasoning levels from the backend', async ({ page }) => {
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

  await expect.poll(() =>
    state.requests.some((request) => request.path === '/api/llm/config/discover-capabilities'),
  ).toBe(true)
  await page.getByRole('combobox', { name: '思考深度' }).click()
  await expect(page.getByRole('option', { name: '默认', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '低', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '中', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '高', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '超高', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '最大', exact: true })).toBeVisible()
})

test('@byok keeps the backend conservative capability when a selected-model probe is unavailable', async ({ page }) => {
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
  await page.getByRole('button', { name: '报告' }).click()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await expect(page.getByText('8.1')).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 PDF' })).toHaveCount(1)
  const viewToggle = page.getByRole('group', { name: '工作区视图' })
  await expect(viewToggle.getByRole('button', { name: '面试' })).toHaveCount(1)
  await expect(viewToggle.getByRole('button', { name: '报告' })).toHaveCount(1)
  await page.emulateMedia({ media: 'print' })
  await page.locator('body').evaluate((body) => body.classList.add('is-printing-report'))
  await expect(page.locator('.app-layout__main')).toHaveCSS('overflow', 'visible')
  await page.locator('body').evaluate((body) => body.classList.remove('is-printing-report'))
  await page.emulateMedia({ media: 'screen' })
  await page.evaluate(() => {
    window.print = () => {
      document.body.dataset.printCalled = 'true'
    }
  })
  await page.getByRole('button', { name: '导出 PDF' }).click()
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
    const data =
      path.endsWith('/radar')
        ? { technical: 8.2, expression: 7.6, logic: 8.4, sessionCount: 5 }
        : path.endsWith('/trend')
          ? [
              { sessionId: 1, createdAt: '2026-08-01T00:00:00Z', technical: 7, expression: 6, logic: 8 },
              { sessionId: 2, createdAt: '2026-08-08T00:00:00Z', technical: 8, expression: 7, logic: 8 },
            ]
          : [{ category: '容量估算', count: 2, descriptions: ['补充量化依据。'] }]
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: ok(data),
    })
  })
  await page.goto('/analytics')

  await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '分数趋势' })).toBeVisible()
  await expect(page.getByRole('img', { name: /技术能力 8\.2/ })).toHaveCount(1)
  await expect(page.getByRole('img', { name: '最近 2 场面试的分数趋势' })).toHaveCount(1)
  await expect(page.getByText('最近 5 场均分')).toHaveCount(3)
  await expect(page.getByText('结构')).toBeVisible()
  await expect(page.getByText('走势')).toBeVisible()
  await expect(page.getByText('聚合')).toBeVisible()

  const typography = await page.locator('.analytics-score-card').first().evaluate((card) => {
    const label = getComputedStyle(card.querySelector('.analytics-score-card__label')!)
    const value = getComputedStyle(card.querySelector('.analytics-score-card__value')!)
    const meta = getComputedStyle(card.querySelector('.analytics-score-card__meta')!)
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
  expect(typography.meta).toMatchObject({ size: '13px', weight: '400' })
  expect(typography.meta.family).toContain('Inter')
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

  await expect(page.locator('.report-plain-text')).toContainText('"expression":7')
  await expect(page.getByText('6.0')).toHaveCount(0)
  await expect(page.getByText('破冰')).toHaveCount(0)
})

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}
