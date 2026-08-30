export const SESSION_PREFERENCES_KEY = 'prelude-interview-session-preferences'

const UNSCOPED_PINNED_KEY = 'pinnedSessionIds'
const UNSCOPED_HIDDEN_KEY = 'deletedSessionIds'

export type SessionPreferences = {
  pinnedIds: number[]
  hiddenIds: number[]
}

export function sessionPreferencesKey(accountScope: string) {
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
