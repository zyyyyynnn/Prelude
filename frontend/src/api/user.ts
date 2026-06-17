import { http } from './http'
import type { ApiResult, UserProfilePayload, UserProfileResponse } from './contracts'
import { unwrapResult } from './contracts'

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
