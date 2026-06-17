import { http } from './http'
import type {
  ApiResult,
  InterviewChatRequest,
  InterviewFinishResponse,
  InterviewMessageRecord,
  InterviewMessageRole,
  InterviewSessionDetailResponse,
  InterviewSessionItem,
  InterviewStartPayload,
  InterviewStageName,
  InterviewStartResponse,
} from './contracts'
import { unwrapResult } from './contracts'

type ChatStreamHandlers = {
  onChunk?: (chunk: string) => void
  onEvent?: (event: ChatStreamEvent) => void
}

export type ChatStreamEvent = {
  eventName: string
  data: string
}

function normalizeStageName(value: unknown): InterviewStageName | undefined {
  if (value === 'warmup' || value === 'technical' || value === 'deep_dive' || value === 'closing') {
    return value
  }
  return undefined
}

function normalizeMessageRole(role: unknown): InterviewMessageRole {
  if (role === 'system' || role === 'assistant') {
    return role
  }
  return 'user'
}

export async function startInterview(payload: InterviewStartPayload) {
  const response = await http.post<ApiResult<InterviewStartResponse>>('/interview/start', payload)
  return unwrapResult(response.data)
}

export async function fetchInterviewSessions() {
  const response = await http.get<ApiResult<InterviewSessionItem[]>>('/interview/sessions')
  const data = unwrapResult(response.data)
  return data.map((item) => ({
    ...item,
    sessionId: item.sessionId,
    targetPosition: item.targetPosition ?? item.positionName ?? '',
    currentStage: normalizeStageName(item.currentStage),
  }))
}

export async function fetchInterviewMessages(sessionId: number) {
  const response = await http.get<ApiResult<InterviewSessionDetailResponse>>(`/interview/${sessionId}/messages`)
  const data = unwrapResult(response.data)
  return {
    ...data,
    currentStage: normalizeStageName(data.currentStage),
    stages: (data.stages || []).map((stage) => ({
      ...stage,
      stageName: normalizeStageName(stage.stageName) || 'warmup',
    })),
    messages: (data.messages || []).map((message): InterviewMessageRecord => ({
      ...message,
      role: normalizeMessageRole(message.role),
    })),
  }
}

export async function finishInterview(sessionId: number) {
  const response = await http.post<ApiResult<InterviewFinishResponse>>(
    `/interview/${sessionId}/finish`,
  )
  return unwrapResult(response.data)
}

function parseSseEvent(rawEvent: string): ChatStreamEvent {
  const lines = rawEvent.split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return { eventName, data }
}

function dispatchSseEvent(event: ChatStreamEvent, handlers: ChatStreamHandlers) {
  handlers.onEvent?.(event)
  if (event.eventName === 'error') {
    throw new Error(event.data || '流式返回错误')
  }
  if (event.eventName === 'message') {
    handlers.onChunk?.(event.data)
  }
}

async function performStream(
  token: string,
  sessionId: number,
  payload: InterviewChatRequest,
  autoStart: boolean,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
) {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const suffix = autoStart ? '?autoStart=true' : ''
  const response = await fetch(`${apiBaseUrl}/interview/${sessionId}/chat${suffix}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (response.status === 401) {
    const error = new Error('登录已失效，请重新登录。')
    ;(error as Error & { status?: number }).status = 401
    throw error
  }

  if (!response.ok || !response.body) {
    throw new Error('流式接口请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const rawEvent = buffer.slice(0, boundary).trim()
        buffer = buffer.slice(boundary + 2)
        if (rawEvent) {
          dispatchSseEvent(parseSseEvent(rawEvent), handlers)
        }
        boundary = buffer.indexOf('\n\n')
      }
    }

    if (buffer.trim()) {
      dispatchSseEvent(parseSseEvent(buffer.trim()), handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

export async function streamInterviewChat(
  token: string,
  sessionId: number,
  payload: InterviewChatRequest,
  autoStart = false,
  handlers: ChatStreamHandlers = {},
  signal?: AbortSignal,
) {
  let attempt = 0
  const maxRetries = 3
  let delay = 1000

  while (true) {
    try {
      await performStream(token, sessionId, payload, autoStart, handlers, signal)
      break
    } catch (error) {
      if (signal?.aborted) {
        throw error
      }
      if (error instanceof Error && (error as any).status === 401) {
        throw error
      }

      // Check if we need to do silent check
      handlers.onEvent?.({ eventName: 'status', data: 'checking' })

      try {
        const detail = await fetchInterviewMessages(sessionId)
        const serverMsgs = detail.messages || []

        if (detail.status === 'generating' || detail.status === 'finished') {
          handlers.onEvent?.({ eventName: 'sync', data: JSON.stringify(serverMsgs) })
          break
        }

        // If user message exists on the server, we align state and finish
        const userMsgExists = autoStart
          ? serverMsgs.some((m) => m.role === 'assistant')
          : serverMsgs.some((m) => m.role === 'user' && m.content === payload.content)

        if (userMsgExists) {
          handlers.onEvent?.({ eventName: 'sync', data: JSON.stringify(serverMsgs) })
          break
        }
      } catch (checkError) {
        console.error('Silent state check failed:', checkError)
      }

      if (attempt >= maxRetries) {
        throw error
      }

      attempt++
      handlers.onEvent?.({ eventName: 'status', data: `reconnecting_${attempt}` })

      await new Promise((resolve) => setTimeout(resolve, delay))
      delay = Math.min(delay * 2, 5000)
    }
  }
}
