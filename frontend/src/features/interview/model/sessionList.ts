import type { SessionPreferences } from './sessionPreferences'

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
