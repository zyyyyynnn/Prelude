import { apiBaseUrl, apiRequest, csrfToken } from '@/shared/api/client'
import type { CurrentUser, LoginResponse } from './types'

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

/**
 * Bootstrap session check. A 401 here is an anonymous visitor, not an expired
 * session, so it must not trigger the global unauthorized handling.
 */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const token = csrfToken()
  const response = await fetch(new URL(`${apiBaseUrl}/auth/me`, window.location.origin), {
    credentials: 'include',
    headers: token ? { 'X-XSRF-TOKEN': token } : undefined,
  }).catch(() => null)
  if (!response || !response.ok) return null
  const payload = (await response.json().catch(() => null)) as { data?: CurrentUser } | null
  return payload?.data ?? null
}
