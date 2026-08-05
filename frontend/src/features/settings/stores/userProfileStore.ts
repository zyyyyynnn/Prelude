import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getErrorMessage } from '@/shared/lib/errors'
import type { AsyncStatus } from '@/shared/lib/async-status'
import { fetchUserProfile, updateUserProfile, uploadUserAvatar } from '../api/user'
import type { UserProfilePayload, UserProfileResponse } from '../model/types'

const PROFILE_MAX_AGE_MS = 5 * 60 * 1000
type MutationLane = 'profile' | 'theme' | 'avatar'
type MutationState = {
  controller: AbortController | null
  revision: number
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export const useUserProfileStore = defineStore('user-profile', () => {
  const activeAccountScope = ref('')
  const profile = ref<UserProfileResponse | null>(null)
  const status = ref<AsyncStatus>('idle')
  const loadError = ref<string | null>(null)
  const refreshError = ref<string | null>(null)
  const refreshing = ref(false)
  const lastLoadedAt = ref<number | null>(null)
  const requestGeneration = ref(0)
  const dataRevision = ref(0)
  const mutationRevision = ref(0)
  const activeAbortController = ref<AbortController | null>(null)
  const profileMutationPending = ref(false)
  const themeMutationPending = ref(false)
  const avatarMutationPending = ref(false)
  let inFlightPromise: Promise<UserProfileResponse | null> | null = null
  let inFlightScope = ''
  let inFlightGeneration = 0
  let inFlightController: AbortController | null = null
  const lanes: Record<MutationLane, MutationState> = {
    profile: { controller: null, revision: 0 },
    theme: { controller: null, revision: 0 },
    avatar: { controller: null, revision: 0 },
  }

  const hasProfile = computed(() => profile.value !== null)
  const mutationPending = computed(
    () => profileMutationPending.value || themeMutationPending.value || avatarMutationPending.value,
  )
  // Kept as a read-only compatibility view; mutation failures stay in the owning component.
  const error = computed(() => loadError.value ?? refreshError.value)

  function pendingRef(lane: MutationLane) {
    return lane === 'profile'
      ? profileMutationPending
      : lane === 'theme'
        ? themeMutationPending
        : avatarMutationPending
  }

  function activateAccount(scope: string) {
    const normalizedScope = scope.trim()
    if (normalizedScope === activeAccountScope.value) return

    activeAbortController.value?.abort()
    for (const lane of Object.values(lanes)) lane.controller?.abort()
    activeAbortController.value = null
    inFlightPromise = null
    inFlightScope = ''
    inFlightController = null
    for (const lane of Object.keys(lanes) as MutationLane[]) {
      lanes[lane].controller = null
      pendingRef(lane).value = false
    }
    requestGeneration.value++
    dataRevision.value = 0
    mutationRevision.value = 0
    activeAccountScope.value = normalizedScope
    profile.value = null
    status.value = 'idle'
    loadError.value = null
    refreshError.value = null
    refreshing.value = false
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

  function isCurrentLoad(scope: string, generation: number, revision: number) {
    return (
      activeAccountScope.value === scope &&
      requestGeneration.value === generation &&
      dataRevision.value === revision
    )
  }

  function ensureLoaded(force = false): Promise<UserProfileResponse | null> {
    if (!activeAccountScope.value) return Promise.resolve(null)
    if (!force && isFresh()) return Promise.resolve(profile.value)
    if (
      inFlightPromise &&
      inFlightScope === activeAccountScope.value &&
      inFlightGeneration === requestGeneration.value
    ) {
      return inFlightPromise
    }

    const scope = activeAccountScope.value
    const generation = requestGeneration.value
    const revision = dataRevision.value
    const controller = new AbortController()
    const hasExistingProfile = profile.value !== null
    activeAbortController.value = controller
    inFlightScope = scope
    inFlightGeneration = generation
    inFlightController = controller
    if (hasExistingProfile) {
      refreshing.value = true
      refreshError.value = null
    } else {
      status.value = 'loading'
      loadError.value = null
    }

    const promise = (async () => {
      try {
        const result = await fetchUserProfile(controller.signal)
        if (isCurrentLoad(scope, generation, revision) && !controller.signal.aborted) {
          profile.value = result
          status.value = 'success'
          loadError.value = null
          refreshError.value = null
          lastLoadedAt.value = Date.now()
        }
        return isCurrentLoad(scope, generation, revision) ? result : profile.value
      } catch (requestError) {
        if (isAbortError(requestError) || controller.signal.aborted) return profile.value
        if (isCurrentLoad(scope, generation, revision)) {
          const message = getErrorMessage(requestError)
          if (hasExistingProfile) {
            refreshError.value = message
            status.value = 'success'
          } else {
            loadError.value = message
            status.value = 'error'
          }
        }
        throw requestError
      } finally {
        if (activeAbortController.value === controller) {
          activeAbortController.value = null
          refreshing.value = false
        }
        if (inFlightController === controller) {
          inFlightPromise = null
          inFlightScope = ''
          inFlightController = null
        }
      }
    })()
    inFlightPromise = promise
    return promise
  }

  async function refresh() {
    if (!activeAccountScope.value) return null
    return ensureLoaded(true)
  }

  function isCurrentMutation(
    lane: MutationLane,
    scope: string,
    generation: number,
    revision: number,
    controller: AbortController,
  ) {
    return (
      activeAccountScope.value === scope &&
      requestGeneration.value === generation &&
      lanes[lane].revision === revision &&
      lanes[lane].controller === controller
    )
  }

  async function runMutation<T>(
    lane: MutationLane,
    request: (signal: AbortSignal) => Promise<T>,
    applyResult: (result: T) => void,
  ) {
    if (!activeAccountScope.value) throw new Error('未登录')
    const state = lanes[lane]
    if (state.controller && lane !== 'avatar') {
      throw new Error('该操作正在进行中')
    }
    state.controller?.abort()
    const scope = activeAccountScope.value
    const generation = requestGeneration.value
    const revision = ++state.revision
    const controller = new AbortController()
    state.controller = controller
    pendingRef(lane).value = true
    mutationRevision.value++
    try {
      const result = await request(controller.signal)
      if (
        isCurrentMutation(lane, scope, generation, revision, controller) &&
        !controller.signal.aborted
      ) {
        applyResult(result)
        dataRevision.value++
        status.value = 'success'
        lastLoadedAt.value = Date.now()
        loadError.value = null
        refreshError.value = null
      }
      return result
    } finally {
      if (isCurrentMutation(lane, scope, generation, revision, controller)) {
        state.controller = null
        pendingRef(lane).value = false
      }
    }
  }

  function mergeProfileFields(result: UserProfileResponse, payload: UserProfilePayload) {
    if (!profile.value) {
      profile.value = result
      return
    }
    const next = { ...profile.value }
    if ('username' in payload) next.username = result.username
    if ('email' in payload) next.email = result.email
    if ('themePreference' in payload) next.themePreference = result.themePreference
    profile.value = next
  }

  function updateProfile(payload: UserProfilePayload) {
    const lane: MutationLane =
      payload.themePreference && Object.keys(payload).length === 1 ? 'theme' : 'profile'
    return runMutation(
      lane,
      (signal) => updateUserProfile(payload, signal),
      (result) => mergeProfileFields(result, payload),
    )
  }

  function uploadAvatar(file: File) {
    return runMutation(
      'avatar',
      (signal) => uploadUserAvatar(file, signal),
      (result) => {
        if (!profile.value) profile.value = result
        else profile.value = { ...profile.value, avatarUrl: result.avatarUrl }
      },
    )
  }

  function reset() {
    activateAccount('')
  }

  function clearError() {
    loadError.value = null
    refreshError.value = null
  }

  return {
    activeAccountScope,
    profile,
    status,
    error,
    loadError,
    refreshError,
    refreshing,
    lastLoadedAt,
    requestGeneration,
    dataRevision,
    mutationRevision,
    mutationPending,
    profileMutationPending,
    themeMutationPending,
    avatarMutationPending,
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
