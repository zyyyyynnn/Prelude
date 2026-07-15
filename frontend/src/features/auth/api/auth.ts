import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { LoginResponse } from '../model/types'

export async function login(username: string, password: string) {
  const response = await http.post<ApiResult<LoginResponse>>('/auth/login', {
    username,
    password,
  })
  return unwrapResult(response.data)
}

export async function register(username: string, password: string, email?: string) {
  const response = await http.post<ApiResult<void>>('/auth/register', {
    username,
    password,
    email,
  })
  return unwrapResult(response.data)
}
