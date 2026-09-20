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
