import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmConfigTestPayload,
  LlmConfigTestResponse,
  LlmModelDiscoveryPayload,
  LlmModelDiscoveryResponse,
  LlmProviderResponse,
} from '../model/types'
import { mapProviderResponses } from '../model/provider'

export async function fetchProviders() {
  const response = await http.get<ApiResult<LlmProviderResponse[]>>('/llm/providers')
  return mapProviderResponses(unwrapResult(response.data))
}

export async function fetchUserLlmConfig() {
  const response = await http.get<ApiResult<LlmConfigResponse>>('/user/llm-config')
  return unwrapResult(response.data)
}

export async function saveUserLlmConfig(payload: LlmConfigPayload) {
  const response = await http.put<ApiResult<LlmConfigResponse>>('/user/llm-config', payload)
  return unwrapResult(response.data)
}

export async function discoverLlmModels(payload: LlmModelDiscoveryPayload) {
  const response = await http.post<ApiResult<LlmModelDiscoveryResponse>>(
    '/user/llm-config/discover-models',
    payload,
  )
  return unwrapResult(response.data)
}

export async function testUserLlmConfig(payload?: LlmConfigTestPayload) {
  const response = await http.post<ApiResult<LlmConfigTestResponse>>(
    '/user/llm-config/test',
    payload ?? {},
  )
  return unwrapResult(response.data)
}
