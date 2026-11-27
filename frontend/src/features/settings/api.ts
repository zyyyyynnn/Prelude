import { apiRequest } from '@/shared/api/client'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmCapabilityDiscoveryPayload,
  LlmModelDiscoveryPayload,
  LlmModelDiscoveryResponse,
  LlmProviderResponse,
  ModelCapabilityResponse,
} from './types'

export const fetchProviders = () => apiRequest<LlmProviderResponse[]>('/llm/providers')

export const fetchLlmConfig = () => apiRequest<LlmConfigResponse>('/llm/config')

export const saveLlmConfig = (payload: LlmConfigPayload) =>
  apiRequest<LlmConfigResponse>('/llm/config', { method: 'PUT', body: JSON.stringify(payload) })

export const discoverModels = (payload: LlmModelDiscoveryPayload) =>
  apiRequest<LlmModelDiscoveryResponse>('/llm/config/discover-models', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const discoverCapabilities = (payload: LlmCapabilityDiscoveryPayload) =>
  apiRequest<ModelCapabilityResponse>('/llm/config/discover-capabilities', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
