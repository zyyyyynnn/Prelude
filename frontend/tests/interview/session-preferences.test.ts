import assert from 'node:assert/strict'
import test from 'node:test'

import {
  readSessionPreferences,
  SESSION_PREFERENCES_KEY,
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

void test('migrates legacy pinned and deleted session ids without data loss', () => {
  const storage = new MemoryStorage()
  storage.setItem('pinnedSessionIds', '[3,1,3]')
  storage.setItem('deletedSessionIds', '[9]')

  const preferences = readSessionPreferences(storage)
  assert.deepEqual(preferences, { pinnedIds: [3, 1], hiddenIds: [9] })

  writeSessionPreferences(storage, preferences)
  assert.equal(storage.getItem('pinnedSessionIds'), null)
  assert.equal(storage.getItem('deletedSessionIds'), null)
  assert.deepEqual(JSON.parse(storage.getItem(SESSION_PREFERENCES_KEY) ?? '{}'), preferences)
})

void test('ignores malformed or non-numeric persisted session ids', () => {
  const storage = new MemoryStorage()
  storage.setItem(SESSION_PREFERENCES_KEY, '{invalid')
  storage.setItem('pinnedSessionIds', '[1,"2",null]')

  assert.deepEqual(readSessionPreferences(storage), { pinnedIds: [1], hiddenIds: [] })
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
