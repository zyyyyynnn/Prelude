import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { UserProfilePayload, UserProfileResponse } from '../model/types'

export async function fetchUserProfile() {
  const response = await http.get<ApiResult<UserProfileResponse>>('/user/profile')
  return unwrapResult(response.data)
}

export async function updateUserProfile(payload: UserProfilePayload) {
  const response = await http.put<ApiResult<UserProfileResponse>>('/user/profile', payload)
  return unwrapResult(response.data)
}

export async function uploadUserAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResult<UserProfileResponse>>('/user/avatar', form)
  return unwrapResult(response.data)
}
