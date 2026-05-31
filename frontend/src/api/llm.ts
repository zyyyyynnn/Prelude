import { http } from './http'
import type {
  ApiResult,
  LlmConfigPayload,
  LlmConfigResponse,
  LlmConfigTestResponse,
  LlmProviderOption,
  LlmProviderRecord,
} from './contracts'
import { unwrapResult } from './contracts'

function normalizeProviderKey(provider: LlmProviderRecord) {
  const rawProvider = provider as LlmProviderRecord & Record<string, unknown>
  const candidate =
    rawProvider.providerKey ||
    rawProvider.provider_key ||
    rawProvider.key ||
    rawProvider.name ||
    rawProvider.displayName ||
    rawProvider.display_name
  return typeof candidate === 'string' && candidate ? candidate : 'provider'
}

function normalizeProviderDisplayName(provider: LlmProviderRecord) {
  const rawProvider = provider as LlmProviderRecord & Record<string, unknown>
  const candidate =
    rawProvider.displayName ||
    rawProvider.display_name ||
    rawProvider.providerName ||
    rawProvider.provider_name ||
    rawProvider.name ||
    rawProvider.providerKey ||
    rawProvider.provider_key
  return typeof candidate === 'string' && candidate ? candidate : '未命名 Provider'
}

function normalizeModelValue(model: string | Record<string, unknown>) {
  if (typeof model === 'string') {
    return model
  }

  const candidate =
    model.key ||
    model.model ||
    model.model_name ||
    model.id ||
    model.name ||
    model.displayName ||
    model.display_name
  return typeof candidate === 'string' ? candidate : ''
}

export function normalizeProviders(payload: unknown): LlmProviderOption[] {
  if (!Array.isArray(payload)) {
    return []
  }

  return payload
    .map((provider) => {
      if (!provider || typeof provider !== 'object') {
        return null
      }

      const record = provider as LlmProviderRecord
      const rawModels = record.models || record.availableModels || []
      const models = rawModels.map((item) =>
        normalizeModelValue(item as string | Record<string, unknown>),
      )
        .filter((item): item is string => Boolean(item))

      return {
        providerKey: normalizeProviderKey(record),
        displayName: normalizeProviderDisplayName(record),
        models,
      }
    })
    .filter((item): item is LlmProviderOption => Boolean(item))
}

export async function fetchProviders() {
  const response = await http.get<ApiResult<unknown>>('/llm/providers')
  return normalizeProviders(unwrapResult(response.data))
}

export async function fetchUserLlmConfig() {
  const response = await http.get<ApiResult<LlmConfigResponse>>('/user/llm-config')
  return unwrapResult(response.data)
}

export async function saveUserLlmConfig(payload: LlmConfigPayload) {
  const response = await http.put<ApiResult<LlmConfigResponse>>('/user/llm-config', payload)
  return unwrapResult(response.data)
}

export async function testUserLlmConfig() {
  const response = await http.post<ApiResult<LlmConfigTestResponse>>('/user/llm-config/test')
  return unwrapResult(response.data)
}
