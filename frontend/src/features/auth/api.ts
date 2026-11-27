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

export const fetchOAuthProviders = () => apiRequest<string[]>('/auth/oauth/providers')

export type CurrentUserCheck =
  | { status: 'authenticated'; user: CurrentUser }
  | { status: 'anonymous' | 'unavailable' }

/**
 * Bootstrap session check. A 401 here is an anonymous visitor, not an expired
 * session, so it must not trigger the global unauthorized handling.
 */
export async function fetchCurrentUser(signal?: AbortSignal): Promise<CurrentUserCheck> {
  try {
    const token = csrfToken()
    const response = await fetch(new URL(`${apiBaseUrl}/auth/me`, window.location.origin), {
      credentials: 'include',
      headers: token ? { 'X-XSRF-TOKEN': token } : undefined,
      signal,
    })
    if (response.status === 401) return { status: 'anonymous' }
    if (!response.ok) return { status: 'unavailable' }
    const payload = (await response.json()) as { data?: CurrentUser } | null
    if (
      typeof payload?.data?.accountId !== 'number' ||
      !Number.isFinite(payload.data.accountId) ||
      typeof payload.data.username !== 'string'
    ) {
      return { status: 'unavailable' }
    }
    return { status: 'authenticated', user: payload.data }
  } catch {
    return { status: 'unavailable' }
  }
}
