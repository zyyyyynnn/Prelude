import assert from 'node:assert/strict'
import test from 'node:test'

import {
  readSessionPreferences,
  SESSION_PREFERENCES_KEY,
  sessionPreferencesKey,
  writeSessionPreferences,
} from '../../src/features/interview/model/sessionPreferences.ts'
import { groupSessions } from '../../src/features/interview/model/sessionList.ts'

class MemoryStorage implements Storage {
  #values = new Map<string, string>()

  get length() {
    return this.#values.size
  }

  clear() {
    this.#values.clear()
  }

  getItem(key: string) {
    return this.#values.get(key) ?? null
  }

  key(index: number) {
    return [...this.#values.keys()][index] ?? null
  }

  removeItem(key: string) {
    this.#values.delete(key)
  }

  setItem(key: string, value: string) {
    this.#values.set(key, value)
  }
}

void test('migrates legacy preferences into the active account scope', () => {
  const storage = new MemoryStorage()
  storage.setItem('pinnedSessionIds', '[3,1,3]')
  storage.setItem('deletedSessionIds', '[9]')

  const preferences = readSessionPreferences(storage, 'user:42')
  assert.deepEqual(preferences, { pinnedIds: [3, 1], hiddenIds: [9] })

  writeSessionPreferences(storage, 'user:42', preferences)
  assert.equal(storage.getItem('pinnedSessionIds'), null)
  assert.equal(storage.getItem('deletedSessionIds'), null)
  assert.equal(storage.getItem(SESSION_PREFERENCES_KEY), null)
  assert.deepEqual(
    JSON.parse(storage.getItem(sessionPreferencesKey('user:42')) ?? '{}'),
    preferences,
  )
})

void test('ignores malformed or non-positive persisted session ids', () => {
  const storage = new MemoryStorage()
  storage.setItem(sessionPreferencesKey('user:42'), '{invalid')
  storage.setItem('pinnedSessionIds', '[1,"2",0,-1,null]')

  assert.deepEqual(readSessionPreferences(storage, 'user:42'), {
    pinnedIds: [1],
    hiddenIds: [],
  })
})

void test('keeps pinned and hidden preferences isolated between accounts', () => {
  const storage = new MemoryStorage()
  writeSessionPreferences(storage, 'user:1', { pinnedIds: [11], hiddenIds: [12] })
  writeSessionPreferences(storage, 'user:2', { pinnedIds: [21], hiddenIds: [] })

  assert.deepEqual(readSessionPreferences(storage, 'user:1'), {
    pinnedIds: [11],
    hiddenIds: [12],
  })
  assert.deepEqual(readSessionPreferences(storage, 'user:2'), {
    pinnedIds: [21],
    hiddenIds: [],
  })
})

void test('groups visible sessions by status with pinned sessions first', () => {
  const sessions = [
    { sessionId: 1, status: 'ongoing' },
    { sessionId: 2, status: 'finished' },
    { sessionId: 3, status: 'ongoing' },
    { sessionId: 4, status: 'finished' },
  ]

  assert.deepEqual(groupSessions(sessions, { pinnedIds: [3, 4], hiddenIds: [2] }), {
    active: [sessions[2], sessions[0]],
    finished: [sessions[3]],
  })
})
