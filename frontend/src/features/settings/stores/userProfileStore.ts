import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getErrorMessage } from '@/shared/lib/errors'
import type { AsyncStatus } from '@/shared/lib/async-status'
import { fetchUserProfile, updateUserProfile, uploadUserAvatar } from '../api/user'
import type { UserProfilePayload, UserProfileResponse } from '../model/types'

const PROFILE_MAX_AGE_MS = 5 * 60 * 1000

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export const useUserProfileStore = defineStore('user-profile', () => {
  const activeAccountScope = ref('')
  const profile = ref<UserProfileResponse | null>(null)
  const status = ref<AsyncStatus>('idle')
  const error = ref<string | null>(null)
  const refreshing = ref(false)
  const lastLoadedAt = ref<number | null>(null)
  const requestGeneration = ref(0)
  const mutationRevision = ref(0)
  const mutationPending = ref(false)
  const activeAbortController = ref<AbortController | null>(null)
  let inFlightPromise: Promise<UserProfileResponse | null> | null = null
  let inFlightScope = ''
  let inFlightGeneration = 0
  let mutationAbortController: AbortController | null = null

  const hasProfile = computed(() => profile.value !== null)

  function activateAccount(scope: string) {
    const normalizedScope = scope.trim()
    if (normalizedScope === activeAccountScope.value) return

    activeAbortController.value?.abort()
    mutationAbortController?.abort()
    activeAbortController.value = null
    mutationAbortController = null
    inFlightPromise = null
    inFlightScope = ''
    requestGeneration.value++
    mutationRevision.value = 0
    activeAccountScope.value = normalizedScope
    profile.value = null
    status.value = 'idle'
    error.value = null
    refreshing.value = false
    mutationPending.value = false
    lastLoadedAt.value = null
  }

  function isFresh() {
    return (
      status.value === 'success' &&
      profile.value !== null &&
      lastLoadedAt.value !== null &&
      Date.now() - lastLoadedAt.value < PROFILE_MAX_AGE_MS
    )
  }

  function isCurrent(scope: string, generation: number, revision: number) {
    return (
      activeAccountScope.value === scope &&
      requestGeneration.value === generation &&
      mutationRevision.value === revision
    )
  }

  function ensureLoaded(force = false): Promise<UserProfileResponse | null> {
    if (!activeAccountScope.value) return Promise.resolve(null)
    if (!force && isFresh()) return Promise.resolve(profile.value)
    if (inFlightPromise && inFlightScope === activeAccountScope.value) return inFlightPromise

    const scope = activeAccountScope.value
    const generation = requestGeneration.value
    const revision = mutationRevision.value
    const controller = new AbortController()
    activeAbortController.value = controller
    inFlightScope = scope
    if (profile.value) {
      refreshing.value = true
    } else {
      status.value = 'loading'
    }
    error.value = null

    inFlightPromise = (async () => {
      try {
        const result = await fetchUserProfile(controller.signal)
        if (isCurrent(scope, generation, revision) && !controller.signal.aborted) {
          profile.value = result
          status.value = 'success'
          error.value = null
          lastLoadedAt.value = Date.now()
        }
        return isCurrent(scope, generation, revision) ? result : profile.value
      } catch (requestError) {
        if (isAbortError(requestError) || controller.signal.aborted) {
          return profile.value
        }
        if (isCurrent(scope, generation, revision)) {
          error.value = getErrorMessage(requestError)
          status.value = profile.value ? 'success' : 'error'
        }
        throw requestError
      } finally {
        if (activeAbortController.value === controller) {
          activeAbortController.value = null
        }
        if (inFlightScope === scope && inFlightGeneration === generation) {
          inFlightPromise = null
          inFlightScope = ''
        }
        if (activeAccountScope.value === scope && requestGeneration.value === generation) {
          refreshing.value = false
        }
      }
    })()
    inFlightGeneration = generation
    return inFlightPromise
  }

  async function refresh() {
    if (!activeAccountScope.value) return null
    return ensureLoaded(true)
  }

  async function runMutation<T>(
    request: (signal: AbortSignal) => Promise<T>,
    applyResult: (result: T) => void,
  ) {
    if (!activeAccountScope.value) throw new Error('未登录')
    const scope = activeAccountScope.value
    const generation = requestGeneration.value
    const revision = ++mutationRevision.value
    mutationAbortController?.abort()
    const controller = new AbortController()
    mutationAbortController = controller
    mutationPending.value = true
    error.value = null
    try {
      const result = await request(controller.signal)
      if (isCurrent(scope, generation, revision) && !controller.signal.aborted) {
        applyResult(result)
        status.value = 'success'
        lastLoadedAt.value = Date.now()
        error.value = null
      }
      return result
    } catch (requestError) {
      if (
        !isAbortError(requestError) &&
        !controller.signal.aborted &&
        isCurrent(scope, generation, revision)
      ) {
        error.value = getErrorMessage(requestError)
      }
      throw requestError
    } finally {
      if (mutationAbortController === controller) {
        mutationAbortController = null
        mutationPending.value = false
      }
    }
  }

  async function updateProfile(payload: UserProfilePayload) {
    return runMutation(
      (signal) => updateUserProfile(payload, signal),
      (result) => {
        profile.value = result
      },
    )
  }

  async function uploadAvatar(file: File) {
    return runMutation(
      (signal) => uploadUserAvatar(file, signal),
      (result) => {
        profile.value = result
      },
    )
  }

  function reset() {
    activateAccount('')
  }

  function clearError() {
    error.value = null
  }

  return {
    activeAccountScope,
    profile,
    status,
    error,
    refreshing,
    lastLoadedAt,
    requestGeneration,
    mutationRevision,
    mutationPending,
    activeAbortController,
    hasProfile,
    activateAccount,
    ensureLoaded,
    refresh,
    updateProfile,
    uploadAvatar,
    reset,
    clearError,
  }
})
