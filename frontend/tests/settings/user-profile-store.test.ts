import { beforeEach, describe, expect, it, vi } from 'vite-plus/test'
import { createPinia, setActivePinia } from 'pinia'
import { useUserProfileStore } from '../../src/features/settings/stores/userProfileStore'
import type { UserProfileResponse } from '../../src/features/settings/model/types'

const api = vi.hoisted(() => ({
  fetchUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
  uploadUserAvatar: vi.fn(),
}))

vi.mock('../../src/features/settings/api/user', () => api)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function profile(username: string, avatarUrl = ''): UserProfileResponse {
  return {
    username,
    email: `${username}@example.com`,
    avatarUrl,
    themePreference: 'system',
  }
}

describe('userProfileStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.fetchUserProfile.mockReset()
    api.updateUserProfile.mockReset()
    api.uploadUserAvatar.mockReset()
  })

  it('deduplicates same-account loads and reuses the resolved profile', async () => {
    api.fetchUserProfile.mockResolvedValue(profile('demo'))
    const store = useUserProfileStore()
    store.activateAccount('user:1')

    const first = store.ensureLoaded()
    const second = store.ensureLoaded()

    const [firstResult, secondResult] = await Promise.all([first, second])
    expect(firstResult).toEqual(secondResult)
    expect(api.fetchUserProfile).toHaveBeenCalledTimes(1)
    expect(store.profile?.username).toBe('demo')
    expect(store.status).toBe('success')
  })

  it('aborts account A and ignores its late response after switching to account B', async () => {
    const accountA = deferred<UserProfileResponse>()
    const signals: AbortSignal[] = []
    api.fetchUserProfile.mockImplementation((signal: AbortSignal) => {
      signals.push(signal)
      return signals.length === 1 ? accountA.promise : Promise.resolve(profile('account-b'))
    })
    const store = useUserProfileStore()
    store.activateAccount('user:1')
    const accountARequest = store.ensureLoaded()

    store.activateAccount('user:2')
    const accountBRequest = store.ensureLoaded()
    accountA.resolve(profile('account-a'))
    await Promise.all([accountARequest, accountBRequest])

    expect(signals[0].aborted).toBe(true)
    expect(store.profile?.username).toBe('account-b')
    expect(store.activeAccountScope).toBe('user:2')
  })

  it('keeps old data during a failed refresh and clears refreshing after a stale race', async () => {
    api.fetchUserProfile.mockResolvedValueOnce(profile('before-refresh'))
    const store = useUserProfileStore()
    store.activateAccount('user:1')
    await store.ensureLoaded()

    const refresh = deferred<UserProfileResponse>()
    api.fetchUserProfile.mockReturnValueOnce(refresh.promise)
    const refreshRequest = store.refresh()
    expect(store.refreshing).toBe(true)
    expect(store.profile?.username).toBe('before-refresh')

    api.updateUserProfile.mockResolvedValue(profile('after-mutation'))
    await store.updateProfile({ username: 'after-mutation' })
    refresh.resolve(profile('stale-refresh'))
    await refreshRequest

    expect(store.profile?.username).toBe('after-mutation')
    expect(store.refreshing).toBe(false)

    api.fetchUserProfile.mockRejectedValueOnce(new Error('offline'))
    await expect(store.refresh()).rejects.toThrow('offline')
    expect(store.profile?.username).toBe('after-mutation')
    expect(store.status).toBe('success')
    expect(store.error).toBe('offline')
  })

  it('atomically stores the server canonical avatar response without an object URL', async () => {
    api.fetchUserProfile.mockResolvedValue(profile('demo'))
    api.uploadUserAvatar.mockResolvedValue(profile('demo', '/media/avatars/1_new-avatar.png'))
    const store = useUserProfileStore()
    store.activateAccount('user:1')
    await store.ensureLoaded()

    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    await store.uploadAvatar(file)

    expect(store.profile?.avatarUrl).toBe('/media/avatars/1_new-avatar.png')
    expect(api.uploadUserAvatar).toHaveBeenCalledWith(file, expect.any(AbortSignal))
    expect(JSON.stringify(store.profile)).not.toContain('blob:')
  })

  it('reset aborts pending work and clears account-owned state', async () => {
    const pending = deferred<UserProfileResponse>()
    let signal!: AbortSignal
    api.fetchUserProfile.mockImplementation((requestSignal: AbortSignal) => {
      signal = requestSignal
      return pending.promise
    })
    const store = useUserProfileStore()
    store.activateAccount('user:1')
    const request = store.ensureLoaded()

    store.reset()
    pending.resolve(profile('late-response'))
    await request

    expect(signal.aborted).toBe(true)
    expect(store.activeAccountScope).toBe('')
    expect(store.profile).toBeNull()
    expect(store.status).toBe('idle')
    expect(store.error).toBeNull()
  })
})
