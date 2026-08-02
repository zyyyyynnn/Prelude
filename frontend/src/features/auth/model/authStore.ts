import { defineStore } from 'pinia'

type AuthState = {
  token: string
  userId: number | null
}

function legacyAccountScope(token: string) {
  let hash = 0x811c9dc5
  for (let index = 0; index < token.length; index++) {
    hash ^= token.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return `legacy:${(hash >>> 0).toString(16)}`
}

function jwtSubject(token: string) {
  const parts = token.split('.')
  if (parts.length !== 3 || typeof globalThis.atob !== 'function') return null

  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const payload: unknown = JSON.parse(globalThis.atob(padded))
    if (!payload || typeof payload !== 'object') return null

    const subject = (payload as Record<string, unknown>).sub
    if (typeof subject !== 'string' && typeof subject !== 'number') return null
    const normalized = String(subject).trim()
    return normalized || null
  } catch {
    return null
  }
}

export function accountScopeForSession(token: string, userId?: number | null) {
  if (!token) return ''
  if (Number.isInteger(userId)) return `user:${userId}`

  // This unverified claim is used only to partition local UI state for sessions
  // persisted before the login response exposed userId. Authorization remains server-side.
  const subject = jwtSubject(token)
  return subject ? `user:${subject}` : legacyAccountScope(token)
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    userId: null,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    accountScope: (state) => accountScopeForSession(state.token, state.userId),
  },
  actions: {
    setSession(token: string, userId: number) {
      // Write identity before the token so a synchronous account-scope watcher
      // never observes a temporary legacy scope for opaque tokens.
      this.userId = userId
      this.token = token
    },
    clearSession() {
      this.token = ''
      this.userId = null
    },
  },
  persist: {
    key: 'auth',
  },
})
