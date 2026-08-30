export type ApiResult<T> = {
  code: number
  message?: string
  data: T
}

export type ProblemDetail = {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
}

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status?: number,
    readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const STREAM_TIMEOUT_MS = 120_000
const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete'])
let onUnauthorized = () => undefined as void | Promise<void>

export function configureApi(options: { onUnauthorized: () => void | Promise<void> }) {
  onUnauthorized = options.onUnauthorized
}

export function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

function withCsrfHeader(headers: Headers, method: string) {
  if (UNSAFE_METHODS.has(method.toLowerCase())) {
    const token = csrfToken()
    if (token && !headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token)
  }
}

async function apiError(response: Response, fallback: string) {
  if (response.status === 401) await onUnauthorized()
  const problem = (await response.json().catch(() => null)) as ProblemDetail | null
  return new ApiClientError(problem?.detail || fallback, response.status, problem?.code)
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit & { query?: Record<string, string | number | undefined> } = {},
): Promise<T> {
  const url = new URL(`${apiBaseUrl}${path}`, window.location.origin)
  Object.entries(init.query ?? {}).forEach(([key, value]) => {
    if (value !== undefined) url.searchParams.set(key, String(value))
  })

  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  withCsrfHeader(headers, init.method ?? 'get')

  const response = await fetch(url, { ...init, headers, credentials: 'include' })
  if (!response.ok) throw await apiError(response, '请求失败')
  const payload = (await response.json().catch(() => null)) as ApiResult<T> | null
  if (!payload || payload.code !== 200) {
    throw new ApiClientError(payload?.message || '请求失败', response.status, 'invalid_response')
  }
  return payload.data
}

export async function streamRequest(
  path: string,
  body: unknown,
  onEvent: (event: { name: string; data: string }) => void,
  signal?: AbortSignal,
) {
  const controller = new AbortController()
  let timedOut = false
  const abort = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', abort, { once: true })
  const timeout = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, STREAM_TIMEOUT_MS)
  try {
    const headers = new Headers({ 'Content-Type': 'application/json' })
    withCsrfHeader(headers, 'post')
    const response = await fetch(`${apiBaseUrl}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
    })
    if (!response.ok) throw await apiError(response, '流式接口请求失败')
    if (!response.body) throw new ApiClientError('流式接口未返回响应内容', response.status)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const raw = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const lines = raw.split('\n')
        const name =
          lines
            .find((line) => line.startsWith('event:'))
            ?.slice(6)
            .trim() || 'message'
        const data = lines
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart())
          .join('\n')
        onEvent({ name, data })
        boundary = buffer.indexOf('\n\n')
      }
    }
  } catch (error) {
    if (timedOut) throw new ApiClientError('流式响应超时，请重试')
    throw error
  } finally {
    window.clearTimeout(timeout)
    signal?.removeEventListener('abort', abort)
  }
}
