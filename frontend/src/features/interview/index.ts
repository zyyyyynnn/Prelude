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
export const setSessionPinned = (id: number, pinned: boolean) =>
  apiRequest<void>(`/interview/${id}/pin`, {
    method: 'PATCH',
    body: JSON.stringify({ pinned }),
  })
export const deleteSession = (id: number) =>
  apiRequest<void>(`/interview/${id}`, { method: 'DELETE' })
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

type SessionListItem = {
  sessionId: number
  status?: string
}

/**
 * The backend orders sessions with pinned ones first, so grouping only splits
 * by status and preserves that order.
 */
export function groupSessions<T extends SessionListItem>(sessions: T[]) {
  return {
    active: sessions.filter((session) => session.status !== 'finished'),
    finished: sessions.filter((session) => session.status === 'finished'),
  }
}

export { AnswerComposerSurface } from './components/AnswerComposerSurface'
export { InterviewContextFacts } from './components/PromptBarControls'
export { InterviewSetupComposer } from './components/InterviewSetupComposer'
export { voiceStatusLabel } from './voiceStatusLabel'

export type { InterviewModelConfig, InterviewModelProvider, InterviewSessionItem } from './types'
