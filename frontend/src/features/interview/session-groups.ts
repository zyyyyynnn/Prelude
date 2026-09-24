import type { InterviewSessionStatus } from './types'

type SessionListItem = {
  sessionId: number
  status?: InterviewSessionStatus
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
