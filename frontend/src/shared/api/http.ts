import axios, { AxiosError, type AxiosInstance } from 'axios'

export type HttpRuntime = {
  getAccessToken: () => string
  onUnauthorized: () => void | Promise<void>
}

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

let runtime: HttpRuntime = {
  getAccessToken: () => '',
  onUnauthorized: () => undefined,
}
let interceptorsBound = false

export function configureHttp(options: HttpRuntime) {
  runtime = options
  if (interceptorsBound) {
    return
  }

  interceptorsBound = true

  let isRedirectingToLogin = false

  http.interceptors.request.use((config) => {
    const token = runtime.getAccessToken()

    if (token) {
      if (config.headers) {
        config.headers.Authorization = `Bearer ${token}`
      }
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
        if (!isRedirectingToLogin) {
          isRedirectingToLogin = true
          try {
            await runtime.onUnauthorized()
          } finally {
            isRedirectingToLogin = false
          }
        }
      }

      const message = error.response?.data?.message || error.message || '请求失败'
      return Promise.reject(
        new ApiClientError(message, error.response?.data?.code, error.response?.status),
      )
    },
  )
}
