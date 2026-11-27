import { apiRequest } from '@/shared/api/client'
import type { UserProfilePayload, UserProfileResponse } from './types'

export const fetchProfile = (signal?: AbortSignal) =>
  apiRequest<UserProfileResponse>('/user/profile', { signal })

export const saveProfile = (payload: UserProfilePayload) =>
  apiRequest<UserProfileResponse>('/user/profile', { method: 'PUT', body: JSON.stringify(payload) })

export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<UserProfileResponse>('/user/avatar', { method: 'POST', body: form })
}
