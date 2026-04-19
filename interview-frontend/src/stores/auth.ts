import { defineStore } from 'pinia'

type AuthState = {
  token: string
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    setToken(token: string) {
      this.token = token
    },
    clearSession() {
      this.token = ''
    },
  },
  persist: true,
})
