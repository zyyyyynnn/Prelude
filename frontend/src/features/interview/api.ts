import { apiRequest, streamRequest } from '@/shared/api/client'
import type {
  InterviewChatRequest,
  InterviewFinishResponse,
  InterviewSessionDetailResponse,
  InterviewSessionItem,
  InterviewStartPayload,
  InterviewStartResponse,
} from './types'

export const fetchSessions = (signal?: AbortSignal) =>
  apiRequest<InterviewSessionItem[]>('/interview/sessions', { signal })
export const fetchSession = (id: number, signal?: AbortSignal) =>
  apiRequest<InterviewSessionDetailResponse>(`/interview/${id}/messages`, { signal })
export const startInterview = (payload: InterviewStartPayload) =>
  apiRequest<InterviewStartResponse>('/interview/start', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
export const finishInterview = (id: number) =>
  apiRequest<InterviewFinishResponse>(`/interview/${id}/finish`, { method: 'POST' })
export const streamInterview = (
  id: number,
  payload: InterviewChatRequest,
  onEvent: (event: { name: string; data: string }) => void,
  signal?: AbortSignal,
  autoStart = false,
) =>
  streamRequest(
    `/interview/${id}/chat${autoStart ? '?autoStart=true' : ''}`,
    payload,
    onEvent,
    signal,
  )
