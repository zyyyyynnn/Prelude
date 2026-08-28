import { expect, test, type Page, type Route } from '@playwright/test'

type ApiState = {
  sessions?: unknown[]
  session?: Record<string, unknown>
  requests: Array<{ path: string; method: string; body: unknown }>
}

const providers = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    availableModels: ['deepseek-chat'],
    enabled: 1,
  },
  {
    providerKey: 'openai-responses',
    displayName: 'OpenAI Responses',
    availableModels: ['gpt-5.4'],
    enabled: 1,
  },
  {
    providerKey: 'openai-chat-completions',
    displayName: 'OpenAI Chat Completions',
    availableModels: ['gpt-4.1'],
    enabled: 1,
  },
  {
    providerKey: 'anthropic-messages',
    displayName: 'Anthropic Messages',
    availableModels: ['claude-sonnet-4-6'],
    enabled: 1,
  },
]

const ok = (data: unknown) => JSON.stringify({ code: 200, message: 'ok', data })

async function installApi(page: Page, state: ApiState) {
  await page.addInitScript(() => localStorage.setItem('prelude-user-id', '1'))
  await page.route('**/api/**', async (route) => respond(route, state))
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
  if (path === '/api/interview/sessions') data = state.sessions ?? []
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
      username: 'prelude',
      email: 'prelude@example.com',
      themePreference: 'system',
    }
  else if (path === '/api/llm/providers') data = providers
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
      providerKey: 'deepseek',
      baseUrl: null,
      model: 'deepseek-chat',
      hasApiKey: false,
      apiKeyMasked: null,
      maxTokens: null,
      thinkingDepth: null,
    }
  else if (path === '/api/interview/start')
    data = {
      sessionId: 11,
      targetPosition: '前端工程师',
      currentStage: 'warmup',
    }
  else if (path === '/api/llm/config' && method === 'PUT')
    data = {
      ...(body as object),
      hasApiKey: true,
      apiKeyMasked: 'sk-***',
      maxTokens: (body as { maxTokens?: number }).maxTokens ?? null,
      thinkingDepth: (body as { thinkingDepth?: string }).thinkingDepth ?? null,
    }
  else if (/\/api\/resume\/improvements\/\d+\/accept$/.test(path)) {
    const improvement = { ...reportImprovement, status: 'accepted' }
    data = {
      improvement,
      resume: {
        resumeId: 1,
        fileName: '候选人简历.pdf',
        documentVersion: 2,
        sourceType: 'pdf',
        document: {},
      },
    }
  } else if (/\/api\/resume\/improvements\/\d+\/reject$/.test(path))
    data = { ...reportImprovement, status: 'rejected' }
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
  expect(removeGeometry).toEqual({
    control: { width: 22, height: 22 },
    icon: { width: 14, height: 14 },
    centered: true,
  })
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
    llmModel: 'deepseek-chat',
    attachmentIds: [51],
  })
})

test('@smoke presents request failures as a dismissible top system toast', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ code: 503, message: '服务暂不可用', data: null }),
    })
  })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.locator('#auth-password').fill('password')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '服务暂不可用' })
  await expect(toast).toBeVisible()
  await toast.hover()
  await expect(page.locator('.auth-form > .notice--error')).toHaveCount(0)
  await expect.poll(async () => (await toast.boundingBox())?.y ?? -1).toBeGreaterThan(0)
  await page.waitForTimeout(450)
  const geometry = await toast.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    const close = element.querySelector<HTMLElement>('[data-close-button]')!.getBoundingClientRect()
    const spacing = Number.parseFloat(
      getComputedStyle(document.documentElement).getPropertyValue('--spacing-sm'),
    )
    return {
      top: rect.top,
      height: rect.height,
      centerDelta: Math.abs(rect.left + rect.width / 2 - window.innerWidth / 2),
      closeCenterDelta: Math.abs(close.top + close.height / 2 - (rect.top + rect.height / 2)),
      closeRightInset: rect.right - close.right,
      expectedRightInset: spacing,
    }
  })
  expect(geometry.top).toBeGreaterThan(0)
  expect(geometry.top).toBeLessThanOrEqual(40)
  expect(geometry.height).toBeGreaterThanOrEqual(49)
  expect(geometry.centerDelta).toBeLessThanOrEqual(1)
  expect(geometry.closeCenterDelta).toBeLessThanOrEqual(1)
  expect(Math.abs(geometry.closeRightInset - geometry.expectedRightInset)).toBeLessThanOrEqual(1)
  await page.screenshot({ path: test.info().outputPath('login-error-toast.png'), fullPage: true })

  await toast.getByRole('button', { name: '关闭系统提示' }).click()
  await expect(toast).toHaveCount(0)
  await expect(page).toHaveURL(/\/login$/)
})

test('@smoke keeps authentication validation out of the form layout', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByRole('button', { name: '登录', exact: true }).last().click()

  const toast = page.locator('[data-sonner-toast]').filter({ hasText: '密码至少需要 6 个字符' })
  await expect(toast).toBeVisible()
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

  await page.getByRole('button', { name: /模型：deepseek-chat，思考深度：默认/ }).click()
  await page.getByRole('menuitem', { name: '管理模型' }).click()
  await expect(page.getByRole('heading', { name: '模型管理' })).toBeVisible()
  await expect(page.getByLabel('服务协议')).toContainText('DeepSeek')
  await expect(page.getByText(/个可用模型/)).toHaveCount(0)
  await expect(page.getByText('尚未保存 API Key')).toHaveCount(0)
  await expect(page.getByRole('status')).toHaveCount(0)
})

test('@smoke centers the async button indicator without resizing the control', async ({ page }) => {
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
        ...body,
        baseUrl: body.baseUrl ?? null,
        hasApiKey: false,
        apiKeyMasked: null,
        maxTokens: body.maxTokens ?? null,
        thinkingDepth: body.thinkingDepth ?? null,
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
        ...body,
        baseUrl: body.baseUrl ?? null,
        hasApiKey: false,
        apiKeyMasked: null,
        maxTokens: body.maxTokens ?? null,
        thinkingDepth: body.thinkingDepth ?? null,
      }),
    })
  })
  await page.goto('/interview')

  let modelTrigger = page.getByRole('button', { name: /模型：/ })
  await modelTrigger.click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  await page.getByRole('menuitemradio', { name: '高', exact: true }).click()

  modelTrigger = page.getByRole('button', { name: /模型：/ })
  expect(await modelTrigger.textContent()).toContain('deepseek-chat · 高')
  await expect(page.getByText('模型配置已更新')).toBeVisible()

  await modelTrigger.click()
  await page.getByRole('menuitem', { name: /思考深度/ }).hover()
  const resetRequest = page.waitForRequest(
    (request) => request.url().endsWith('/api/llm/config') && request.method() === 'PUT',
  )
  await page.getByRole('menuitemradio', { name: '默认', exact: true }).click()
  expect(await page.getByRole('button', { name: /模型：/ }).textContent()).toContain(
    'deepseek-chat · 默认',
  )
  expect((await resetRequest).postDataJSON()).toMatchObject({ thinkingDepth: null })
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
      llmThinkingDepth: 'high',
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

test('@byok sends the exact custom provider DTO', async ({ page }) => {
  const state: ApiState = { requests: [] }
  await installApi(page, state)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  await page.getByLabel('服务协议').click()
  await page.getByRole('option', { name: 'OpenAI Responses' }).click()
  await page.getByLabel('Base URL').fill('https://api.openai.com/v1/responses/')
  await page.getByLabel('模型', { exact: true }).click()
  await page.getByRole('option', { name: 'gpt-5.4' }).click()
  await page.getByLabel('API Key', { exact: true }).fill('sk-test')
  await page.getByRole('button', { name: '保存设置' }).click()
  await expect(page.getByText('LLM 配置已保存')).toBeVisible()
  const save = state.requests.find(
    (request) => request.path === '/api/llm/config' && request.method === 'PUT',
  )
  expect(save?.body).toEqual({
    providerKey: 'openai-responses',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-5.4',
    apiKey: 'sk-test',
    thinkingDepth: null,
  })
})

const reportImprovement = {
  id: 31,
  resumeId: 1,
  sessionId: 11,
  targetPath: 'summary',
  currentText: '负责服务开发',
  proposedText: '负责核心服务并降低故障恢复时间',
  rationale: '补充职责与结果',
  evidence: '回答中描述了故障演练',
  baseDocumentVersion: 1,
  status: 'pending',
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
  markdownFallback: '# 面试训练报告',
  resumeImprovements: [reportImprovement],
})

test('@smoke renders structured reports and applies resume improvements', async ({ page }) => {
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
      llmThinkingDepth: null,
      attachments: [],
    },
  }
  await installApi(page, state)
  await page.goto('/interview?session=11')
  await page.getByRole('button', { name: '报告' }).click()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await expect(page.getByText('8.1')).toBeVisible()
  await page.emulateMedia({ media: 'print' })
  await page.locator('body').evaluate((body) => body.classList.add('is-printing-report'))
  await expect(page.locator('.app-layout__main')).toHaveCSS('overflow', 'visible')
  await expect(page.locator('.report-export-actions')).toHaveCSS('display', 'none')
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
  await expect(page.getByText('已打开系统打印窗口')).toBeVisible()
  await page.getByRole('button', { name: '接受并写入简历' }).click()
  await expect(page.getByText('已接受')).toBeVisible()
  expect(
    state.requests.some((request) => request.path === '/api/resume/improvements/31/accept'),
  ).toBe(true)
})

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}
