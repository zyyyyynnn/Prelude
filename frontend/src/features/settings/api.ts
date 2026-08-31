import { apiRequest } from '@/shared/api/client'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmModelDiscoveryPayload,
  LlmModelDiscoveryResponse,
  LlmProviderResponse,
  UserProfilePayload,
  UserProfileResponse,
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
export const fetchProfile = () => apiRequest<UserProfileResponse>('/user/profile')
export const saveProfile = (payload: UserProfilePayload) =>
  apiRequest<UserProfileResponse>('/user/profile', { method: 'PUT', body: JSON.stringify(payload) })
export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<UserProfileResponse>('/user/avatar', { method: 'POST', body: form })
}
