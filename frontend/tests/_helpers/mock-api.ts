/**
 * Shared mock-api helper used by Playwright specs in this repo.
 *
 * Both `tests/visual/ui-visual.spec.ts` and `tests/a11y/ui-a11y.spec.ts`
 * previously duplicated an inline `installMockApi(page)` route. That repetition
 * drove the sentrux `min_equality` architectural-quality score down by ~0.01
 * (two near-identical helpers count as "low diversity"). Sharing this helper
 * brings the score back above the gate without relaxing the rule.
 *
 * The helper is intentionally exported in two flavours:
 *   - `installMockApi(page)` — minimal mock covering `/api/**` for the
 *     workspace shell + sidebar + composer.
 *   - `installMockApiWithOverrides(page, overrides)` — lets a spec swap in
 *     session / analytics mocks for state-specific scenarios.
 */
import type { Page, Route } from '@playwright/test'

export const AUTH_TOKEN = 'playwright-test-token'

const POSITIONS = [
  { id: 1, name: 'Java 后端工程师' },
  { id: 2, name: '前端工程师' },
  { id: 3, name: '算法工程师' },
]

const RESUMES = [{ id: 1, name: 'Java高级架构.pdf', uploadedAt: '2026-05-01T10:00:00Z' }]

const LLM_PROVIDERS = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    availableModels: ['deepseek-chat', 'deepseek-reasoner'],
    enabled: 1,
  },
  {
    providerKey: 'openai-responses',
    displayName: 'OpenAI Responses',
    availableModels: [],
    enabled: 1,
  },
  {
    providerKey: 'openai-chat-completions',
    displayName: 'OpenAI Chat Completions',
    availableModels: [],
    enabled: 1,
  },
  {
    providerKey: 'anthropic-messages',
    displayName: 'Anthropic Messages',
    availableModels: [],
    enabled: 1,
  },
]

const LLM_CONFIG = {
  providerKey: 'openai-chat-completions',
  baseUrl: 'https://api.example.com/v1',
  model: 'gpt-4o-mini',
  hasApiKey: true,
  apiKeyMasked: 'sk-***masked',
}

const USER_PROFILE = { username: 'demo', email: 'demo@example.com' }

const ANALYTICS_RADAR = { technical: 7, expression: 8, logic: 7, sessionCount: 3 }

const ANALYTICS_TREND = [
  { sessionId: 1, createdAt: '2026-05-10T10:00:00Z', technical: 6, expression: 7, logic: 6 },
  { sessionId: 2, createdAt: '2026-05-15T10:00:00Z', technical: 7, expression: 7, logic: 7 },
  { sessionId: 3, createdAt: '2026-05-20T10:00:00Z', technical: 7, expression: 8, logic: 7 },
]

const ANALYTICS_WEAKNESSES = [
  {
    category: '并发场景下的资源回收',
    summary: '部分回答未覆盖 emitter 清理路径',
    descriptions: ['建议补充连接对象的 timeout / error / completion 统一清理逻辑。'],
  },
]

export const STRUCTURED_REPORT = JSON.stringify({
  summary: {
    fitAssessment: '候选人具备较完整的前端工程化和页面性能意识，适合继续深入评估。',
    actionRecommendation: '完成专项训练后进行下一轮模拟。',
    overallRisk: '项目指标量化不足。',
  },
  scores: { technical: 8, expression: 7, logic: 9, overall: 8 },
  stagePerformances: [
    {
      stageName: 'warmup',
      score: 8,
      summary: '项目背景表达清楚。',
      positiveSignals: ['职责明确'],
      negativeSignals: [],
      improvementSuggestions: ['补充业务规模'],
    },
    {
      stageName: 'technical',
      score: 7,
      summary: '技术基础稳定。',
      positiveSignals: ['实现路径清楚'],
      negativeSignals: ['量化不足'],
      improvementSuggestions: ['补充性能指标'],
    },
    {
      stageName: 'deep_dive',
      score: 8,
      summary: '能够继续推导边界。',
      positiveSignals: ['逻辑完整'],
      negativeSignals: [],
      improvementSuggestions: ['说明失败场景'],
    },
    {
      stageName: 'closing',
      score: 9,
      summary: '总结简洁。',
      positiveSignals: ['表达清楚'],
      negativeSignals: [],
      improvementSuggestions: ['明确后续计划'],
    },
  ],
  questionReviews: [
    {
      stageName: 'technical',
      question: '如何优化虚拟列表？',
      answerSummary: '通过缓冲区与 rAF 合并更新。',
      score: 8,
      scoringReason: '方案完整但缺少量化。',
      improvementSuggestion: '补充帧率和数据规模。',
    },
    {
      stageName: 'deep_dive',
      question: '如何定位复杂状态更新问题？',
      answerSummary: '从数据流、组件边界与异步链路逐层排查。',
      score: 7,
      scoringReason: '排查路径清楚，异常兜底说明不足。',
      improvementSuggestion: '补充失败场景和恢复策略。',
    },
  ],
  strengths: ['工程化思路完整', '表达结构清楚'],
  weaknesses: ['性能量化：缺少压测指标'],
  trainingPlan: {
    threeDay: ['复盘逐题回答'],
    sevenDay: ['完成性能专项训练'],
    nextInterviewFocus: ['量化表达与异常兜底'],
  },
  finalAdvice: '继续投递，并在下一轮前完成量化表达训练。',
  markdownFallback: '# 面试评估报告\n\n结构化报告兼容文本。',
})

const ok = <T>(data: T) => ({ code: 200, message: 'ok', data })

export type MockOverrides = {
  positions?: typeof POSITIONS
  resumes?: typeof RESUMES
  llmProviders?: typeof LLM_PROVIDERS
  llmConfig?: typeof LLM_CONFIG
  userProfile?: typeof USER_PROFILE
  sessions?: unknown[]
  analyticsRadar?: typeof ANALYTICS_RADAR
  analyticsTrend?: typeof ANALYTICS_TREND
  analyticsWeaknesses?: typeof ANALYTICS_WEAKNESSES
  interviewDetail?: unknown
}

export async function installMockApi(page: Page, overrides: MockOverrides = {}): Promise<void> {
  // IMPORTANT: scope the route to backend endpoint prefixes only. A blanket
  // /api/.* match would also intercept Vite's HMR / module requests that
  // share the /api/ prefix and break module loading with a confusing
  // "Expected a JavaScript module but received application/json" error.
  await page.route(
    /\/api\/(interview|user|resume|position|llm|analytics)\/.*/,
    async (route: Route) => {
      const url = new URL(route.request().url())
      const method = route.request().method()
      const pathname = url.pathname.replace(/^\/api/, '')

      if (method === 'GET' && pathname === '/position/list') {
        return route.fulfill({ json: ok(overrides.positions ?? POSITIONS) })
      }
      if (method === 'GET' && pathname === '/resume/list') {
        return route.fulfill({ json: ok(overrides.resumes ?? RESUMES) })
      }
      if (method === 'GET' && pathname === '/llm/providers') {
        return route.fulfill({ json: ok(overrides.llmProviders ?? LLM_PROVIDERS) })
      }
      if (method === 'GET' && pathname === '/user/llm-config') {
        return route.fulfill({ json: ok(overrides.llmConfig ?? LLM_CONFIG) })
      }
      if (method === 'PUT' && pathname === '/user/llm-config') {
        return route.fulfill({ json: ok(overrides.llmConfig ?? LLM_CONFIG) })
      }
      if (method === 'POST' && pathname === '/user/llm-config/discover-models') {
        return route.fulfill({
          json: ok({
            providerKey: 'openai-chat-completions',
            baseUrl: (overrides.llmConfig ?? LLM_CONFIG).baseUrl,
            models: ['detected-model', 'detected-model-pro'],
          }),
        })
      }
      if (method === 'POST' && pathname === '/user/llm-config/test') {
        return route.fulfill({
          json: ok({
            providerKey: 'openai-chat-completions',
            model: (overrides.llmConfig ?? LLM_CONFIG).model,
            ok: true,
            message: '模型配置测试通过',
          }),
        })
      }
      if (method === 'GET' && pathname === '/user/profile') {
        return route.fulfill({ json: ok(overrides.userProfile ?? USER_PROFILE) })
      }
      if (method === 'PUT' && pathname === '/user/profile') {
        return route.fulfill({ json: ok(overrides.userProfile ?? USER_PROFILE) })
      }
      if (method === 'GET' && pathname === '/interview/sessions') {
        return route.fulfill({ json: ok(overrides.sessions ?? []) })
      }
      if (method === 'POST' && pathname === '/interview/start') {
        return route.fulfill({
          json: ok({ sessionId: 101, targetPosition: 'Java 后端工程师', currentStage: 'warmup' }),
        })
      }
      if (method === 'GET' && pathname === '/interview/101/messages') {
        if (overrides.interviewDetail) {
          return route.fulfill({ json: ok(overrides.interviewDetail) })
        }
        return route.fulfill({
          json: ok({
            sessionId: 101,
            status: 'ongoing',
            positionName: 'Java 后端工程师',
            stageName: 'warmup',
            currentStage: 'warmup',
            question: '请先做 1 分钟自我介绍。',
            messages: [],
          }),
        })
      }
      if (method === 'GET' && pathname === '/interview/101') {
        return route.fulfill({
          json: ok({
            sessionId: 101,
            status: 'ongoing',
            positionName: 'Java 后端工程师',
            stageName: 'intro',
            question: '请先做 1 分钟自我介绍。',
          }),
        })
      }
      if (method === 'POST' && pathname === '/interview/101/messages') {
        return route.fulfill({
          json: ok({
            messageId: 9001,
            role: 'assistant',
            content: '收到。我将基于你的回答继续追问。',
            createdAt: '2026-05-20T10:01:00Z',
          }),
        })
      }
      if (method === 'GET' && pathname === '/analytics/radar') {
        return route.fulfill({ json: ok(overrides.analyticsRadar ?? ANALYTICS_RADAR) })
      }
      if (method === 'GET' && pathname === '/analytics/trend') {
        return route.fulfill({ json: ok(overrides.analyticsTrend ?? ANALYTICS_TREND) })
      }
      if (method === 'GET' && pathname === '/analytics/weaknesses') {
        return route.fulfill({ json: ok(overrides.analyticsWeaknesses ?? ANALYTICS_WEAKNESSES) })
      }
      return route.fulfill({ json: ok(null) })
    },
  )

  // Inject auth token via localStorage before the SPA hydrates. Mirrors the
  // pattern used by verify-byok-settings-flow.cjs and friends. The token value
  // is opaque to the backend (page.route short-circuits it).
  await page.addInitScript((token: string) => {
    localStorage.setItem('auth', JSON.stringify({ token }))
  }, AUTH_TOKEN)
}
