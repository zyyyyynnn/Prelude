export const SESSION_PREFERENCES_KEY = 'prelude-interview-session-preferences'

const LEGACY_PINNED_KEY = 'pinnedSessionIds'
const LEGACY_HIDDEN_KEY = 'deletedSessionIds'

export type SessionPreferences = {
  pinnedIds: number[]
  hiddenIds: number[]
}

function parseIds(value: string | null): number[] {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (!Array.isArray(parsed)) return []
    return [...new Set(parsed.filter((item): item is number => Number.isInteger(item)))]
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

export function readSessionPreferences(storage: Storage): SessionPreferences {
  return (
    parsePreferences(storage.getItem(SESSION_PREFERENCES_KEY)) ?? {
      pinnedIds: parseIds(storage.getItem(LEGACY_PINNED_KEY)),
      hiddenIds: parseIds(storage.getItem(LEGACY_HIDDEN_KEY)),
    }
  )
}

export function writeSessionPreferences(storage: Storage, preferences: SessionPreferences) {
  storage.setItem(SESSION_PREFERENCES_KEY, JSON.stringify(preferences))
  storage.removeItem(LEGACY_PINNED_KEY)
  storage.removeItem(LEGACY_HIDDEN_KEY)
}
