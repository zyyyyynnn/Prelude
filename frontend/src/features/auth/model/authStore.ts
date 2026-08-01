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

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    userId: null,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    accountScope: (state) => {
      if (!state.token) return ''
      return Number.isInteger(state.userId)
        ? `user:${state.userId}`
        : legacyAccountScope(state.token)
    },
  },
  actions: {
    setSession(token: string, userId: number) {
      this.token = token
      this.userId = userId
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
