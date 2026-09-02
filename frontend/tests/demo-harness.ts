import type { Page, Route } from '@playwright/test'
import type {
  InterviewSessionDetailResponse,
  InterviewSessionItem,
} from '../src/features/interview/types'
import type {
  LlmConfigResponse,
  LlmProviderResponse,
  ModelCapabilityResponse,
  ReasoningLevel,
} from '../src/features/settings/types'

export const DEMO_VIEWPORT = { width: 1440, height: 900 } as const

export type DemoRequest = {
  path: string
  method: string
  body: unknown
}

export type DemoState = {
  authenticated: boolean
  requests: DemoRequest[]
  sessions: InterviewSessionItem[]
  session: InterviewSessionDetailResponse
  llmConfig: LlmConfigResponse
}

function capability(
  provider: string,
  model: string,
  supportedReasoningLevels: ReasoningLevel[],
  overrides: Partial<ModelCapabilityResponse> = {},
): ModelCapabilityResponse {
  return {
    provider,
    model,
    reasoning: supportedReasoningLevels.length > 1,
    structuredOutput: true,
    toolCalling: true,
    streaming: true,
    vision: false,
    multilingual: true,
    longContext: true,
    embedding: false,
    nativeRealtimeVoice: false,
    supportedReasoningLevels,
    ...overrides,
  }
}

const deepSeekPro = capability('deepseek', 'deepseek-v4-pro', ['AUTO', 'HIGH'])
const deepSeekFlash = capability('deepseek', 'deepseek-v4-flash', ['AUTO', 'HIGH'])
export const demoProviders: LlmProviderResponse[] = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    customEndpoint: false,
    models: [deepSeekPro, deepSeekFlash],
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

const report = JSON.stringify({
  summary: {
    fitAssessment: '具备扎实的后端工程能力，能够清晰拆分系统边界。',
    actionRecommendation: '继续强化容量估算与故障演练的量化表达。',
    overallRisk: '高并发场景的容量依据仍需补充。',
  },
  scores: { technical: 8.6, expression: 8.2, logic: 8.8, overall: 8.5 },
  stagePerformances: [
    {
      stageName: 'warmup',
      score: 8.1,
      summary: '背景介绍完整，项目职责表达清楚。',
      positiveSignals: ['能够快速概括项目目标与个人贡献'],
      negativeSignals: ['业务指标可以更量化'],
      improvementSuggestions: ['用结果数据补充项目影响'],
    },
    {
      stageName: 'technical',
      score: 8.7,
      summary: '服务边界、数据一致性和缓存策略分析扎实。',
      positiveSignals: ['边界清晰', '能够识别一致性风险'],
      negativeSignals: ['容量估算证据不足'],
      improvementSuggestions: ['补充峰值流量与资源预算'],
    },
    {
      stageName: 'closing',
      score: 8.4,
      summary: '复盘结构完整，能够主动指出方案权衡。',
      positiveSignals: ['结论明确'],
      negativeSignals: [],
      improvementSuggestions: ['保持量化表达'],
    },
  ],
  questionReviews: [
    {
      stageName: 'technical',
      question: '如何拆分高并发订单系统的服务边界？',
      answerSummary: '按业务能力拆分，并通过事件驱动降低同步耦合。',
      score: 9,
      scoringReason: '边界意识明确，也覆盖了数据一致性。',
      improvementSuggestion: '补充容量目标和失败恢复时间。',
    },
    {
      stageName: 'technical',
      question: '缓存与数据库不一致时如何处理？',
      answerSummary: '采用失效优先、重试补偿和可观测告警。',
      score: 8.3,
      scoringReason: '覆盖了主要异常路径。',
      improvementSuggestion: '说明不同一致性等级的选择条件。',
    },
  ],
  strengths: ['系统边界意识', '结构化表达', '风险识别'],
  weaknesses: ['容量估算', '量化证据'],
  trainingPlan: {
    threeDay: ['完成一次订单系统容量估算练习'],
    sevenDay: ['复盘缓存一致性与故障恢复方案'],
    nextInterviewFocus: ['用指标说明架构权衡'],
  },
  finalAdvice: '保持当前结构化表达方式，并用流量、延迟和恢复目标增强方案可信度。',
})

export function createDemoState(): DemoState {
  return {
    authenticated: false,
    requests: [],
    sessions: [
      {
        sessionId: 41,
        targetPosition: '平台工程师',
        status: 'finished',
        currentStage: 'closing',
        llmProvider: 'deepseek',
        llmModel: 'deepseek-v4-pro',
        createdAt: '2026-08-22T09:30:00+08:00',
      },
    ],
    session: {
      sessionId: 42,
      targetPosition: 'Java 后端工程师',
      status: 'ongoing',
      currentStage: 'technical',
      summaryReport: '',
      stages: [
        {
          stageName: 'warmup',
          startedAt: '2026-08-30T09:00:00+08:00',
          endedAt: '2026-08-30T09:05:00+08:00',
        },
        {
          stageName: 'technical',
          startedAt: '2026-08-30T09:05:00+08:00',
          endedAt: null,
        },
      ],
      messages: [
        {
          id: 1,
          role: 'assistant',
          content: '请结合实际项目，说明你会如何拆分高并发订单系统的服务边界。',
          seqNum: 1,
          createdAt: '2026-08-30T09:06:00+08:00',
        },
      ],
      resumeId: 1,
      positionId: 1,
      jdText: '负责 Java 服务端架构、稳定性建设与性能优化。',
      llmThinkingDepth: 'high',
      attachments: [],
    },
    llmConfig: {
      provider: 'deepseek',
      model: 'deepseek-v4-pro',
      customEndpointUrl: null,
      hasApiKey: false,
      apiKeyMasked: null,
      reasoningLevel: 'HIGH',
      fallbackModels: [],
      capability: deepSeekPro,
    },
  }
}

export async function installDemoHarness(page: Page, state: DemoState) {
  await page.context().route(/^https?:\/\/[^/]+\/api\//, async (route) => respond(route, state))
}

async function respond(route: Route, state: DemoState) {
  const request = route.request()
  const path = new URL(request.url()).pathname
  const method = request.method()
  const rawBody = request.postData()
  const body = rawBody && request.headers()['content-type']?.includes('application/json')
    ? (JSON.parse(rawBody) as unknown)
    : null
  state.requests.push({ path, method, body })

  if (path === '/api/auth/me' && method === 'GET') {
    if (!state.authenticated) return fulfillProblem(route, 401, 'authentication_required', '请先登录')
    return fulfillJson(route, { accountId: 1, username: 'demo' })
  }
  if (path === '/api/auth/login' && method === 'POST') {
    state.authenticated = true
    return fulfillJson(route, { accountId: 1 })
  }
  if (path === '/api/auth/logout' && method === 'POST') {
    state.authenticated = false
    return fulfillJson(route, null)
  }
  if (path === '/api/interview/start' && method === 'POST') {
    const active = sessionSummary(state.session)
    state.sessions = [active, ...state.sessions.filter((item) => item.sessionId !== active.sessionId)]
    return fulfillJson(route, { sessionId: state.session.sessionId, currentStage: state.session.currentStage })
  }
  if (/\/api\/interview\/\d+\/chat$/.test(path) && method === 'POST') {
    const answer = (body as { content?: string } | null)?.content?.trim() || '候选人回答'
    const assistant = '边界分析很清楚。最后请总结你会如何验证容量目标与故障恢复能力。'
    state.session.messages = [
      ...state.session.messages,
      {
        id: 2,
        role: 'user',
        content: answer,
        seqNum: 2,
        createdAt: '2026-08-30T09:08:00+08:00',
        score: 8.6,
        hint: '结构完整，可继续补充容量数据。',
      },
      {
        id: 3,
        role: 'assistant',
        content: assistant,
        seqNum: 3,
        createdAt: '2026-08-30T09:08:10+08:00',
      },
    ]
    state.session.currentStage = 'closing'
    state.session.stages = state.session.stages.map((stage) =>
      stage.stageName === 'technical'
        ? { ...stage, endedAt: '2026-08-30T09:08:00+08:00' }
        : stage,
    )
    state.session.stages.push({
      stageName: 'closing',
      startedAt: '2026-08-30T09:08:00+08:00',
      endedAt: null,
    })
    state.sessions = state.sessions.map((item) =>
      item.sessionId === state.session.sessionId ? sessionSummary(state.session) : item,
    )
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: message\ndata: ${assistant}`,
        'event: judge\ndata: {"score":8.6,"hint":"结构完整，可继续补充容量数据。"}',
        '',
      ].join('\n\n'),
    })
    return
  }
  if (/\/api\/interview\/\d+\/finish$/.test(path) && method === 'POST') {
    state.session.status = 'finished'
    state.session.summaryReport = report
    state.session.stages = state.session.stages.map((stage) =>
      stage.stageName === 'closing'
        ? { ...stage, endedAt: '2026-08-30T09:10:00+08:00' }
        : stage,
    )
    state.sessions = state.sessions.map((item) =>
      item.sessionId === state.session.sessionId ? sessionSummary(state.session) : item,
    )
    return fulfillJson(route, {
      sessionId: state.session.sessionId,
      summaryReport: report,
      status: 'finished',
    })
  }

  if (path === '/api/interview/sessions' && method === 'GET')
    return fulfillJson(route, state.sessions)
  if (/\/api\/interview\/\d+\/messages$/.test(path) && method === 'GET')
    return fulfillJson(route, state.session)
  if (path === '/api/position/list' && method === 'GET')
    return fulfillJson(route, [
      { id: 1, name: 'Java 后端工程师', editable: false },
      { id: 2, name: '平台工程师', editable: true },
    ])
  if (path === '/api/resume/list' && method === 'GET')
    return fulfillJson(route, [
      {
        id: 1,
        fileName: 'Java 后端候选人简历.pdf',
        createdAt: '2026-08-20T10:00:00+08:00',
        sessionCount: 3,
        inUse: true,
      },
      {
        id: 2,
        fileName: '项目经历补充.pdf',
        createdAt: '2026-08-25T16:30:00+08:00',
        sessionCount: 0,
        inUse: false,
      },
    ])
  if (path === '/api/user/profile' && method === 'GET')
    return fulfillJson(route, {
      accountId: 1,
      username: 'demo',
      email: 'demo@prelude.local',
      avatarUrl: null,
      themePreference: 'light',
      revision: 0,
    })
  if (path === '/api/llm/providers' && method === 'GET') return fulfillJson(route, demoProviders)
  if (path === '/api/llm/config' && method === 'GET') return fulfillJson(route, state.llmConfig)
  if (path === '/api/llm/config' && method === 'PUT') {
    const saved = body as Partial<LlmConfigResponse>
    state.llmConfig = {
      ...state.llmConfig,
      provider: saved.provider ?? state.llmConfig.provider,
      model: saved.model ?? state.llmConfig.model,
      reasoningLevel: saved.reasoningLevel ?? state.llmConfig.reasoningLevel,
    }
    return fulfillJson(route, state.llmConfig)
  }
  if (path === '/api/analytics/radar' && method === 'GET')
    return fulfillJson(route, { technical: 8.6, expression: 8.2, logic: 8.8, sessionCount: 6 })
  if (path === '/api/analytics/trend' && method === 'GET')
    return fulfillJson(route, [
      { sessionId: 36, createdAt: '2026-07-26T10:00:00+08:00', technical: 6.8, expression: 7.1, logic: 7.2 },
      { sessionId: 37, createdAt: '2026-08-02T10:00:00+08:00', technical: 7.2, expression: 7.3, logic: 7.5 },
      { sessionId: 38, createdAt: '2026-08-09T10:00:00+08:00', technical: 7.6, expression: 7.5, logic: 7.9 },
      { sessionId: 39, createdAt: '2026-08-16T10:00:00+08:00', technical: 7.9, expression: 7.8, logic: 8.1 },
      { sessionId: 40, createdAt: '2026-08-23T10:00:00+08:00', technical: 8.2, expression: 8, logic: 8.4 },
      { sessionId: 42, createdAt: '2026-08-30T10:00:00+08:00', technical: 8.6, expression: 8.2, logic: 8.8 },
    ])
  if (path === '/api/analytics/weaknesses' && method === 'GET')
    return fulfillJson(route, [
      { category: '容量估算', count: 3, descriptions: ['需要补充峰值流量和资源预算。'] },
      { category: '故障恢复', count: 2, descriptions: ['需要明确恢复时间与恢复点目标。'] },
      { category: '量化表达', count: 1, descriptions: ['用指标说明架构权衡。'] },
    ])

  return fulfillProblem(route, 501, 'not_implemented', `Demo harness 未处理 ${method} ${path}`)
}

function sessionSummary(session: InterviewSessionDetailResponse): InterviewSessionItem {
  return {
    sessionId: session.sessionId,
    targetPosition: session.targetPosition,
    status: session.status,
    currentStage: session.currentStage,
    llmProvider: 'deepseek',
    llmModel: 'deepseek-v4-pro',
    llmThinkingDepth: session.llmThinkingDepth,
    createdAt: '2026-08-30T09:00:00+08:00',
    summaryReport: session.summaryReport,
  }
}

async function fulfillProblem(route: Route, status: number, code: string, detail: string) {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', title: code, status, detail, code }),
  })
}

async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'ok', data }),
  })
}
