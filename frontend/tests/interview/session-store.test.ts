import { beforeEach, describe, expect, it, vi } from 'vite-plus/test'
import { createPinia, setActivePinia } from 'pinia'
import { useInterviewSessionStore } from '../../src/features/interview/stores/sessionStore'
import type { InterviewSessionDetailResponse } from '../../src/features/interview/model/types'

const api = vi.hoisted(() => ({
  fetchInterviewMessages: vi.fn(),
  fetchInterviewSessions: vi.fn(),
}))

vi.mock('../../src/features/interview/api/interview', () => api)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function storage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => values.delete(key),
    setItem: (key, value) => values.set(key, value),
  }
}

function detail(sessionId: number): InterviewSessionDetailResponse {
  return {
    sessionId,
    targetPosition: `岗位 ${sessionId}`,
    status: 'finished',
    currentStage: 'closing',
    stages: [],
    messages: [],
  }
}

describe('interview session detail state', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.fetchInterviewMessages.mockReset()
    api.fetchInterviewSessions.mockReset()
  })

  it('keeps the failed initial target and retries that target without a fake empty state', async () => {
    api.fetchInterviewMessages.mockRejectedValueOnce(new Error('session unavailable'))
    const store = useInterviewSessionStore()
    store.activateAccount('user:1', storage())

    await expect(store.loadSession(42)).rejects.toThrow('session unavailable')

    expect(store.requestedSessionId).toBe(42)
    expect(store.failedSessionId).toBe(42)
    expect(store.activeSessionId).toBeNull()
    expect(store.replay).toBeNull()
    expect(store.sessionDetailStatus).toBe('error')
    expect(store.sessionDetailError).toBe('session unavailable')

    api.fetchInterviewMessages.mockResolvedValueOnce(detail(42))
    await store.loadSession(store.failedSessionId!)

    expect(store.activeSessionId).toBe(42)
    expect(store.requestedSessionId).toBe(42)
    expect(store.failedSessionId).toBeNull()
    expect(store.sessionDetailStatus).toBe('success')
    expect(store.replay?.sessionId).toBe(42)
  })

  it('preserves A while B fails and retries B instead of active A', async () => {
    api.fetchInterviewMessages.mockResolvedValueOnce(detail(1))
    const store = useInterviewSessionStore()
    store.activateAccount('user:1', storage())
    await store.loadSession(1)

    api.fetchInterviewMessages.mockRejectedValueOnce(new Error('B is unavailable'))
    await expect(store.loadSession(2)).rejects.toThrow('B is unavailable')

    expect(store.activeSessionId).toBe(1)
    expect(store.replay?.sessionId).toBe(1)
    expect(store.requestedSessionId).toBe(2)
    expect(store.failedSessionId).toBe(2)
    expect(store.sessionDetailStatus).toBe('success')
    expect(store.sessionDetailError).toBe('B is unavailable')

    api.fetchInterviewMessages.mockResolvedValueOnce(detail(2))
    await store.loadSession(store.failedSessionId!)

    expect(store.activeSessionId).toBe(2)
    expect(store.replay?.sessionId).toBe(2)
    expect(store.failedSessionId).toBeNull()
  })

  it('ignores stale A after B becomes the requested session', async () => {
    const requestA = deferred<InterviewSessionDetailResponse>()
    const requestB = deferred<InterviewSessionDetailResponse>()
    api.fetchInterviewMessages
      .mockReturnValueOnce(requestA.promise)
      .mockReturnValueOnce(requestB.promise)
    const store = useInterviewSessionStore()
    store.activateAccount('user:1', storage())

    const loadA = store.loadSession(1)
    const loadB = store.loadSession(2)
    requestA.resolve(detail(1))
    await loadA
    expect(store.activeSessionId).toBeNull()
    expect(store.requestedSessionId).toBe(2)

    requestB.resolve(detail(2))
    await loadB
    expect(store.activeSessionId).toBe(2)
    expect(store.replay?.sessionId).toBe(2)
  })

  it('clears requested and failed detail state on account switch', async () => {
    const request = deferred<InterviewSessionDetailResponse>()
    api.fetchInterviewMessages.mockReturnValueOnce(request.promise)
    const store = useInterviewSessionStore()
    store.activateAccount('user:1', storage())
    const pending = store.loadSession(7)

    store.activateAccount('user:2', storage())
    request.resolve(detail(7))
    await pending

    expect(store.activeSessionId).toBeNull()
    expect(store.requestedSessionId).toBeNull()
    expect(store.failedSessionId).toBeNull()
    expect(store.replay).toBeNull()
  })
})
