import axios, { AxiosError, type AxiosInstance } from 'axios'
import type { Router } from 'vue-router'
import { useAuthStore } from '../stores/auth'

export class ApiClientError extends Error {
  code?: number
  status?: number

  constructor(message: string, code?: number, status?: number) {
    super(message)
    this.name = 'ApiClientError'
    Object.setPrototypeOf(this, new.target.prototype)
    this.code = code
    this.status = status
  }
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

export const http: AxiosInstance = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
})

let interceptorsBound = false

export function bindHttpInterceptors(router: Router) {
  if (interceptorsBound) {
    return
  }

  interceptorsBound = true

  http.interceptors.request.use((config) => {
    const authStore = useAuthStore()

    if (authStore.token) {
      config.headers = config.headers ?? {}
      config.headers.Authorization = `Bearer ${authStore.token}`
    }

    return config
  })

  http.interceptors.response.use(
    (response) => {
      const payload = response.data as { code?: number; message?: string } | undefined
      if (payload && typeof payload.code === 'number' && payload.code !== 200) {
        return Promise.reject(
          new ApiClientError(payload.message || '请求失败', payload.code, response.status),
        )
      }

      return response
    },
    async (error: AxiosError<{ code?: number; message?: string }>) => {
      if (error.response?.status === 401) {
        const authStore = useAuthStore()
        authStore.clearSession()
        if (router.currentRoute.value.path !== '/login') {
          await router.replace({
            path: '/login',
            query: { reason: 'expired' },
          })
        }
      }

      const message = error.response?.data?.message || error.message || '请求失败'
      return Promise.reject(
        new ApiClientError(message, error.response?.data?.code, error.response?.status),
      )
    },
  )
}
