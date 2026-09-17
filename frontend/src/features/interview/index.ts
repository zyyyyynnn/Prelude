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

type SessionListItem = {
  sessionId: number
  status?: string
}

function pinnedFirst<T extends SessionListItem>(sessions: T[], pinnedIds: Set<number>) {
  return sessions
    .map((session, index) => ({ session, index }))
    .sort((left, right) => {
      const pinDelta =
        Number(pinnedIds.has(right.session.sessionId)) -
        Number(pinnedIds.has(left.session.sessionId))
      return pinDelta || left.index - right.index
    })
    .map(({ session }) => session)
}

export function groupSessions<T extends SessionListItem>(
  sessions: T[],
  preferences: SessionPreferences,
) {
  const hiddenIds = new Set(preferences.hiddenIds)
  const pinnedIds = new Set(preferences.pinnedIds)
  const visible = sessions.filter((session) => !hiddenIds.has(session.sessionId))

  return {
    active: pinnedFirst(
      visible.filter((session) => session.status !== 'finished'),
      pinnedIds,
    ),
    finished: pinnedFirst(
      visible.filter((session) => session.status === 'finished'),
      pinnedIds,
    ),
  }
}

const SESSION_PREFERENCES_KEY = 'prelude-interview-session-preferences'

// Legacy unscoped keys, read once for migration then removed on the next write.
const UNSCOPED_PINNED_KEY = 'pinnedSessionIds'
const UNSCOPED_HIDDEN_KEY = 'deletedSessionIds'

export type SessionPreferences = {
  pinnedIds: number[]
  hiddenIds: number[]
}

function sessionPreferencesKey(accountScope: string) {
  return `${SESSION_PREFERENCES_KEY}:${encodeURIComponent(accountScope)}`
}

function parseIds(value: string | null): number[] {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (!Array.isArray(parsed)) return []
    return [
      ...new Set(
        parsed.filter((item): item is number => Number.isInteger(item) && Number(item) > 0),
      ),
    ]
  } catch {
    return []
  }
}

function parsePreferences(value: string | null): SessionPreferences | null {
  if (!value) return null
  try {
    const parsed: unknown = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object') return null
    const record = parsed as Record<string, unknown>
    return {
      pinnedIds: parseIds(JSON.stringify(record.pinnedIds ?? [])),
      hiddenIds: parseIds(JSON.stringify(record.hiddenIds ?? [])),
    }
  } catch {
    return null
  }
}

export function readSessionPreferences(storage: Storage, accountScope: string): SessionPreferences {
  if (!accountScope) return { pinnedIds: [], hiddenIds: [] }

  const scoped = parsePreferences(storage.getItem(sessionPreferencesKey(accountScope)))
  if (scoped) return scoped

  return (
    parsePreferences(storage.getItem(SESSION_PREFERENCES_KEY)) ?? {
      pinnedIds: parseIds(storage.getItem(UNSCOPED_PINNED_KEY)),
      hiddenIds: parseIds(storage.getItem(UNSCOPED_HIDDEN_KEY)),
    }
  )
}

export function writeSessionPreferences(
  storage: Storage,
  accountScope: string,
  preferences: SessionPreferences,
) {
  if (!accountScope) return
  storage.setItem(sessionPreferencesKey(accountScope), JSON.stringify(preferences))
  storage.removeItem(SESSION_PREFERENCES_KEY)
  storage.removeItem(UNSCOPED_PINNED_KEY)
  storage.removeItem(UNSCOPED_HIDDEN_KEY)
}

export type { InterviewSessionItem } from './types'
