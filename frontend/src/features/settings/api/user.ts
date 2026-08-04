import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { UserProfilePayload, UserProfileResponse } from '../model/types'

export async function fetchUserProfile(signal?: AbortSignal) {
  const response = await http.get<ApiResult<UserProfileResponse>>('/user/profile', { signal })
  return unwrapResult(response.data)
}

export async function updateUserProfile(payload: UserProfilePayload, signal?: AbortSignal) {
  const response = await http.put<ApiResult<UserProfileResponse>>('/user/profile', payload, {
    signal,
  })
  return unwrapResult(response.data)
}

export async function uploadUserAvatar(file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResult<UserProfileResponse>>('/user/avatar', form, { signal })
  return unwrapResult(response.data)
}
