import type { Route } from '@playwright/test'
import type {
  LlmProviderResponse,
  ModelCapabilityResponse,
  ReasoningLevel,
} from '../src/features/settings/types'

export function modelCapability(
  provider: string,
  model: string,
  supportedReasoningLevels: ReasoningLevel[] = ['AUTO', 'LOW', 'HIGH', 'MAX'],
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

export function customModelCapability(
  provider: string,
  model: string,
  supportedReasoningLevels: ReasoningLevel[] = ['AUTO'],
): ModelCapabilityResponse {
  return modelCapability(provider, model, supportedReasoningLevels, {
    structuredOutput: false,
    toolCalling: false,
    multilingual: false,
    longContext: false,
  })
}

export function createProviderFixtures(): LlmProviderResponse[] {
  return [
    {
      providerKey: 'deepseek',
      displayName: 'DeepSeek',
      customEndpoint: false,
      models: [
        modelCapability('deepseek', 'deepseek-v4-pro'),
        modelCapability('deepseek', 'deepseek-v4-flash'),
      ],
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
}

export function jsonBody(data: unknown) {
  return JSON.stringify({ code: 200, message: 'ok', data })
}

export function problemBody(status: number, code: string, detail: string) {
  return JSON.stringify({ type: 'about:blank', title: code, status, detail, code })
}

export async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: jsonBody(data),
  })
}

export async function fulfillProblem(route: Route, status: number, code: string, detail: string) {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: problemBody(status, code, detail),
  })
}

export async function fulfillUnknownEndpoint(route: Route) {
  const request = route.request()
  const path = new URL(request.url()).pathname
  await fulfillProblem(
    route,
    501,
    'not_implemented',
    `Test harness 未处理 ${request.method()} ${path}`,
  )
}
