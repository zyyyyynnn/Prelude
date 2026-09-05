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

const deepSeekPro = capability('deepseek', 'deepseek-v4-pro', ['AUTO', 'LOW', 'HIGH', 'MAX'])
const deepSeekFlash = capability('deepseek', 'deepseek-v4-flash', ['AUTO', 'LOW', 'HIGH', 'MAX'])
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
    fitAssessment: '具备中级 Java 后端岗位所需的事务、幂等和异步处理经验。',
    actionRecommendation: '可进入下一轮，并重点验证故障演练与容量分析。',
    overallRisk: '恢复流程完整，但恢复时间目标和积压阈值缺少历史量化。',
  },
  scores: { technical: 6, expression: 7, logic: 6, overall: 6.3 },
  stagePerformances: [
    {
      stageName: 'warmup',
      score: 7,
      summary: '职责和性能结果表达清楚。',
      positiveSignals: ['说明了负责范围和 P99 改善'],
      negativeSignals: ['数据采集周期未说明'],
      improvementSuggestions: ['补充指标观察窗口'],
    },
    {
      stageName: 'technical',
      score: 7,
      summary: '能够给出可落地的幂等方案。',
      positiveSignals: ['使用唯一键和事务状态推进'],
      negativeSignals: ['首次失败状态处理略简略'],
      improvementSuggestions: ['补充失败重试状态机'],
    },
    {
      stageName: 'deep_dive',
      score: 6,
      summary: '理解事务消息恢复路径。',
      positiveSignals: ['提出出站消息和消费去重'],
      negativeSignals: ['未量化最长恢复时间'],
      improvementSuggestions: ['定义恢复时间和积压阈值'],
    },
    {
      stageName: 'closing',
      score: 6,
      summary: '能将改进方向落到演练。',
      positiveSignals: ['提出中间件故障演练'],
      negativeSignals: ['尚未给出验收目标值'],
      improvementSuggestions: ['为演练设定可验证目标'],
    },
  ],
  questionReviews: [
    {
      stageName: 'warmup',
      question: '请先介绍你在订单履约服务中负责的范围，以及你如何判断改造是否有效。',
      answerSummary: '我负责下单后的履约接口和异步任务。改造前先记录接口 P95、P99 和失败率，完成索引与批量查询调整后，核心接口 P99 从 480ms 降到 210ms，错误率保持在千分之一以内。',
      score: 7,
      scoringReason: '结果和个人职责都清楚，可以再说明数据采集周期。',
      improvementSuggestion: '补充性能数据的采集周期和样本量。',
    },
    {
      stageName: 'technical',
      question: '同一个支付回调重复到达时，你如何保证订单状态只推进一次？',
      answerSummary: '我会用支付单号作为幂等键，先插入带唯一索引的处理记录，再在同一事务里按当前状态更新订单。重复请求命中唯一键后读取已有结果，不再重复发履约消息。',
      score: 7,
      scoringReason: '幂等键和事务边界明确，还可以补充首次处理失败后的重试状态。',
      improvementSuggestion: '说明首次处理失败时幂等记录如何回到可重试状态。',
    },
    {
      stageName: 'deep_dive',
      question: '如果数据库事务提交成功，但消息发布失败，你会怎样恢复？',
      answerSummary: '我会在本地事务中同时写出站消息，由后台任务扫描未发布记录并重试；消费者仍按业务键去重。以前的项目只看重试成功率，没有明确统计最长恢复时间，这是我会补上的指标。',
      score: 6,
      scoringReason: '恢复路径合理，但最长恢复时间和积压告警阈值仍需量化。',
      improvementSuggestion: '给出最长恢复时间、积压阈值和人工介入条件。',
    },
    {
      stageName: 'closing',
      question: '如果下周接手这条链路，你会优先补哪一项可靠性验证？',
      answerSummary: '我会先做消息中间件短时不可用的演练，记录积压量、恢复耗时和重复消费比例，再据此设置告警阈值，并验证人工补偿入口确实可用。',
      score: 6,
      scoringReason: '收尾具体，后续应把恢复目标写进演练验收标准。',
      improvementSuggestion: '把恢复目标写成故障演练的通过条件。',
    },
  ],
  strengths: ['能说明事务和幂等边界', '性能数据表达具体'],
  weaknesses: ['故障恢复量化：尚未定义最长恢复时间和积压告警阈值。'],
  trainingPlan: {
    threeDay: ['整理一次消息发布失败的恢复时序图'],
    sevenDay: ['完成带恢复时间指标的故障演练'],
    nextInterviewFocus: ['异步链路恢复目标与告警设计'],
  },
  finalAdvice: '保持当前工程化表达，并用恢复时间、积压量和人工介入条件补全可靠性论证。',
})

export function createDemoState(): DemoState {
  return {
    authenticated: false,
    requests: [],
    sessions: [
      {
        sessionId: 58,
        targetPosition: '算法工程师',
        status: 'finished',
        currentStage: 'closing',
        createdAt: '2026-09-04T09:00:00+08:00',
      },
      {
        sessionId: 57,
        targetPosition: '前端工程师',
        status: 'finished',
        currentStage: 'closing',
        createdAt: '2026-09-03T14:00:00+08:00',
      },
      {
        sessionId: 56,
        targetPosition: 'Java 后端工程师',
        status: 'finished',
        currentStage: 'closing',
        createdAt: '2026-09-03T10:00:00+08:00',
      },
    ],
    session: {
      sessionId: 62,
      targetPosition: 'Java 后端工程师',
      status: 'ongoing',
      currentStage: 'closing',
      summaryReport: '',
      stages: [
        {
          stageName: 'warmup',
          startedAt: '2026-09-05T10:00:00+08:00',
          endedAt: '2026-09-05T10:08:00+08:00',
        },
        {
          stageName: 'technical',
          startedAt: '2026-09-05T10:08:00+08:00',
          endedAt: '2026-09-05T10:16:00+08:00',
        },
        {
          stageName: 'deep_dive',
          startedAt: '2026-09-05T10:16:00+08:00',
          endedAt: '2026-09-05T10:25:00+08:00',
        },
        {
          stageName: 'closing',
          startedAt: '2026-09-05T10:25:00+08:00',
          endedAt: null,
        },
      ],
      messages: [
        {
          id: 1,
          role: 'assistant',
          content: '请先介绍你在订单履约服务中负责的范围，以及你如何判断改造是否有效。',
          seqNum: 1,
          createdAt: '2026-09-05T10:01:00+08:00',
        },
        {
          id: 2,
          role: 'user',
          content: '我负责下单后的履约接口和异步任务。改造前先记录接口 P95、P99 和失败率，完成索引与批量查询调整后，核心接口 P99 从 480ms 降到 210ms，错误率保持在千分之一以内。',
          seqNum: 2,
          createdAt: '2026-09-05T10:04:00+08:00',
          score: 7,
          hint: '结果和个人职责都清楚，可以再说明数据采集周期。',
        },
        {
          id: 3,
          role: 'assistant',
          content: '同一个支付回调重复到达时，你如何保证订单状态只推进一次？',
          seqNum: 4,
          createdAt: '2026-09-05T10:09:00+08:00',
        },
        {
          id: 4,
          role: 'user',
          content: '我会用支付单号作为幂等键，先插入带唯一索引的处理记录，再在同一事务里按当前状态更新订单。重复请求命中唯一键后读取已有结果，不再重复发履约消息。',
          seqNum: 5,
          createdAt: '2026-09-05T10:13:00+08:00',
          score: 7,
          hint: '幂等键和事务边界明确，还可以补充首次处理失败后的重试状态。',
        },
        {
          id: 5,
          role: 'assistant',
          content: '如果数据库事务提交成功，但消息发布失败，你会怎样恢复？',
          seqNum: 7,
          createdAt: '2026-09-05T10:17:00+08:00',
        },
        {
          id: 6,
          role: 'user',
          content: '我会在本地事务中同时写出站消息，由后台任务扫描未发布记录并重试；消费者仍按业务键去重。以前的项目只看重试成功率，没有明确统计最长恢复时间，这是我会补上的指标。',
          seqNum: 8,
          createdAt: '2026-09-05T10:22:00+08:00',
          score: 6,
          hint: '恢复路径合理，但最长恢复时间和积压告警阈值仍需量化。',
        },
        {
          id: 7,
          role: 'assistant',
          content: '如果下周接手这条链路，你会优先补哪一项可靠性验证？',
          seqNum: 10,
          createdAt: '2026-09-05T10:26:00+08:00',
        },
      ],
      resumeId: 1,
      positionId: 1,
      model: 'deepseek-v4-pro',
      reasoningLevel: 'AUTO',
      jdText: '维护订单与履约服务，要求熟悉 MySQL、Redis、消息队列和可观测性。',
      attachments: [],
    },
    llmConfig: {
      provider: 'deepseek',
      model: 'deepseek-v4-pro',
      customEndpointUrl: null,
      hasApiKey: false,
      apiKeyMasked: null,
      reasoningLevel: 'AUTO',
      maxOutputTokens: 4096,
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
    const assistant = '本场面试已结束，可以生成报告。'
    state.session.messages = [
      ...state.session.messages,
      {
        id: 8,
        role: 'user',
        content: answer,
        seqNum: 11,
        createdAt: '2026-09-05T10:30:00+08:00',
        score: 6,
        hint: '收尾具体，后续应把恢复目标写进演练验收标准。',
      },
      {
        id: 9,
        role: 'assistant',
        content: assistant,
        seqNum: 12,
        createdAt: '2026-09-05T10:30:10+08:00',
      },
    ]
    state.sessions = state.sessions.map((item) =>
      item.sessionId === state.session.sessionId ? sessionSummary(state.session) : item,
    )
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: message\ndata: ${assistant}`,
        'event: judge\ndata: {"score":6,"hint":"收尾具体，后续应把恢复目标写进演练验收标准。"}',
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
        ? { ...stage, endedAt: '2026-09-05T10:31:00+08:00' }
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
        fileName: 'Java 后端工程师简历.pdf',
        createdAt: '2026-09-03T09:00:00+08:00',
        sessionCount: 1,
        inUse: true,
      },
      {
        id: 2,
        fileName: '前端工程师简历.pdf',
        createdAt: '2026-09-03T09:05:00+08:00',
        sessionCount: 1,
        inUse: true,
      },
      {
        id: 3,
        fileName: '算法工程师简历.pdf',
        createdAt: '2026-09-03T09:10:00+08:00',
        sessionCount: 2,
        inUse: true,
      },
    ])
  if (path === '/api/user/profile' && method === 'GET')
    return fulfillJson(route, {
      accountId: 1,
      username: 'demo',
      email: 'demo@prelude.local',
      avatarUrl: null,
      themePreference: 'system',
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
      maxOutputTokens: saved.maxOutputTokens ?? state.llmConfig.maxOutputTokens,
    }
    return fulfillJson(route, state.llmConfig)
  }
  if (path === '/api/analytics/radar' && method === 'GET')
    return fulfillJson(route, { technical: 6.7, expression: 6, logic: 6.3, sessionCount: 3 })
  if (path === '/api/analytics/trend' && method === 'GET')
    return fulfillJson(route, [
      { sessionId: 56, createdAt: '2026-09-03T10:00:00+08:00', technical: 6, expression: 7, logic: 6 },
      { sessionId: 57, createdAt: '2026-09-03T14:00:00+08:00', technical: 8, expression: 6, logic: 7 },
      { sessionId: 58, createdAt: '2026-09-04T09:00:00+08:00', technical: 6, expression: 5, logic: 6 },
    ])
  if (path === '/api/analytics/weaknesses' && method === 'GET')
    return fulfillJson(route, [
      { category: '故障恢复量化', count: 1, descriptions: ['尚未定义最长恢复时间和积压告警阈值。'] },
      { category: '可访问性验证', count: 1, descriptions: ['尚未建立自动化扫描和读屏回归清单。'] },
      { category: '线上实验归因', count: 1, descriptions: ['已有实验缺少分层结果和置信区间。'] },
    ])

  return fulfillProblem(route, 501, 'not_implemented', `Demo harness 未处理 ${method} ${path}`)
}

function sessionSummary(session: InterviewSessionDetailResponse): InterviewSessionItem {
  return {
    sessionId: session.sessionId,
    targetPosition: session.targetPosition,
    status: session.status,
    currentStage: session.currentStage,
    createdAt: '2026-09-05T10:00:00+08:00',
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
