import { apiRequest } from '@/shared/api/client'
import type { LoginResponse } from './types'

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function register(username: string, password: string, email?: string) {
  return apiRequest<void>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, email }),
  })
}

export function logout() {
  return apiRequest<void>('/auth/logout', { method: 'POST' })
}
