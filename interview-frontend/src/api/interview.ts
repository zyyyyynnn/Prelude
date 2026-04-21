import { http } from './http'
import type {
  ApiResult,
  InterviewChatRequest,
  InterviewFinishResponse,
  InterviewMessageRecord,
  InterviewMessageRole,
  InterviewReplayResponse,
  InterviewSessionItem,
  InterviewStageChangePayload,
  InterviewStageChangeResponse,
  InterviewStageName,
  InterviewStartResponse,
} from './contracts'
import { unwrapResult } from './contracts'

type ChatStreamHandlers = {
  onChunk?: (chunk: string) => void
  onDone?: () => void
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

export async function startInterview(payload: { resumeId: number; positionId: number }) {
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
  const response = await http.get<ApiResult<InterviewReplayResponse>>(`/interview/${sessionId}/messages`)
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

export async function changeInterviewStage(
  sessionId: number,
  payload: InterviewStageChangePayload,
) {
  const response = await http.post<ApiResult<InterviewStageChangeResponse>>(
    `/interview/${sessionId}/stage`,
    payload,
  )
  const data = unwrapResult(response.data)
  return {
    ...data,
    stageName: normalizeStageName(data.stageName) || payload.stageName,
  }
}

export async function finishInterview(sessionId: number) {
  const response = await http.post<ApiResult<InterviewFinishResponse>>(
    `/interview/${sessionId}/finish`,
  )
  return unwrapResult(response.data)
}

function parseSseEvent(rawEvent: string) {
  const lines = rawEvent.split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return { eventName, data }
}

export async function streamInterviewChat(
  token: string,
  sessionId: number,
  payload: InterviewChatRequest,
  autoStart = false,
  handlers: ChatStreamHandlers = {},
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
        const { eventName, data } = parseSseEvent(rawEvent)
        if (eventName === 'error') {
          throw new Error(data || '流式返回错误')
        }
        handlers.onChunk?.(data)
      }
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (buffer.trim()) {
    const { eventName, data } = parseSseEvent(buffer.trim())
    if (eventName === 'error') {
      throw new Error(data || '流式返回错误')
    }
    handlers.onChunk?.(data)
  }

  handlers.onDone?.()
}
